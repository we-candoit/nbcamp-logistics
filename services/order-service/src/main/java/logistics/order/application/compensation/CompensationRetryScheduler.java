package logistics.order.application.compensation;

import logistics.order.application.service.OrderTransactionService;
import logistics.order.common.AuthContext;
import logistics.order.domain.entity.OrderItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

//COMPENSATION_FAILED 주문 재보상 스케줄러
//요청이 없는 스레드라 시스템 사용자로 실행 (외부 호출 헤더, 감사 정보)
//fixedDelay라 이전 실행이 끝난 뒤 다음 실행까지 대기 → 실행이 겹치지 않음
@Slf4j
@Component
public class CompensationRetryScheduler {

    private final OrderTransactionService orderTransactionService;
    private final OrderCompensator orderCompensator;
    private final UUID systemUserId;
    private final String systemUserRole;
    private final int maxRetryCount;
    private final int batchSize;
    private final Duration staleWaiting;
    private final Clock clock;

    @Autowired
    public CompensationRetryScheduler(
            OrderTransactionService orderTransactionService,
            OrderCompensator orderCompensator,
            @Value("${order.compensation-retry.system-user-id}") UUID systemUserId,
            @Value("${order.compensation-retry.system-user-role}") String systemUserRole,
            @Value("${order.compensation-retry.max-retry-count}") int maxRetryCount,
            @Value("${order.compensation-retry.batch-size}") int batchSize,
            @Value("${order.compensation-retry.stale-waiting-minutes}") long staleWaitingMinutes
    ) {
        this(orderTransactionService, orderCompensator, systemUserId, systemUserRole,
                maxRetryCount, batchSize, Duration.ofMinutes(staleWaitingMinutes), Clock.systemDefaultZone());
    }

    //테스트에서 기준시각을 고정하기 위해 Clock을 받음
    CompensationRetryScheduler(
            OrderTransactionService orderTransactionService,
            OrderCompensator orderCompensator,
            UUID systemUserId,
            String systemUserRole,
            int maxRetryCount,
            int batchSize,
            Duration staleWaiting,
            Clock clock
    ) {
        this.orderTransactionService = orderTransactionService;
        this.orderCompensator = orderCompensator;
        this.systemUserId = systemUserId;
        this.systemUserRole = systemUserRole;
        this.maxRetryCount = maxRetryCount;
        this.batchSize = batchSize;
        this.staleWaiting = staleWaiting;
        this.clock = clock;
    }

    //오래된 WAITING 주문을 재보상 대상으로 전환한 뒤 재보상 → 전환된 주문은 같은 실행에서 바로 재보상
    @Scheduled(fixedDelayString = "${order.compensation-retry.fixed-delay-ms}")
    public void retry() {
        AuthContext.runAsSystem(systemUserId, systemUserRole, () -> {
            markStaleWaitingOrders();
            recompensateTargets();
        });
    }

    //주문생성 중 서버가 종료되어 WAITING으로 남은 주문 / 정상 주문생성은 기준시간보다 훨씬 짧게 끝남
    private void markStaleWaitingOrders() {
        LocalDateTime threshold = LocalDateTime.now(clock).minus(staleWaiting);
        try {
            List<UUID> markedOrderIds = orderTransactionService.markStaleWaitingAsCompensationFailed(threshold);
            for (UUID orderId : markedOrderIds) {
                log.warn("오래된 WAITING 주문을 재보상 대상으로 전환 orderId={} threshold={}", orderId, threshold);
            }
        } catch (RuntimeException e) {
            //전환에 실패해도 기존 재보상 대상은 처리
            log.error("오래된 WAITING 주문 전환 실패 threshold={}", threshold, e);
        }
    }

    //대상 주문을 하나씩 재보상 / 한 주문에서 예외가 나도 나머지는 계속 처리
    private void recompensateTargets() {
        List<OrderItem> targets;
        try {
            targets = orderTransactionService.findRecompensationTargets(maxRetryCount, batchSize);
        } catch (RuntimeException e) {
            log.error("주문 재보상 대상 조회 실패", e);
            return;
        }

        //대상이 없으면 로그를 남기지 않음 (실행마다 빈 로그가 쌓이지 않도록)
        if (targets.isEmpty()) {
            return;
        }
        log.info("주문 재보상 시작 대상={}건", targets.size());

        for (OrderItem target : targets) {
            try {
                orderCompensator.recompensateCreate(target, maxRetryCount);
            } catch (RuntimeException e) {
                log.error("주문 재보상 중 예외 orderId={}", target.getOrder().getId(), e);
            }
        }
    }
}

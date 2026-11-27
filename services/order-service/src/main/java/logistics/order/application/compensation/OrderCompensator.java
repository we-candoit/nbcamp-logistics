package logistics.order.application.compensation;

import logistics.order.application.service.ClientService;
import logistics.order.application.service.OrderTransactionService;
import logistics.order.domain.entity.OrderItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

//주문 보상시나리오 / 외부 서비스 호출 실패시 앞서 처리한 단계를 되돌림
//보상 중 예외는 원래 실패 원인을 가리지 않도록 밖으로 던지지 않음
//외부 서비스 보상 호출의 재시도는 Feign Retryer가 담당, DB 복구는 재시도 없이 실패시 로그만 남김
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCompensator {

    private final ClientService clientService;
    private final OrderTransactionService orderTransactionService;

    //로그 형식: [분류] 대상 결과 (이유) | 키=값
    //결과는 시작 / 완료 / 생략 / 실패 / 수동 처리 필요 중 하나
    private static final String CREATE = "[보상]";
    private static final String RECOMPENSATE = "[재보상]";
    private static final String CANCEL = "[주문취소]";
    private static final String DELETE = "[주문삭제]";

    //주문생성 실패시 성공한 단계를 역순으로 되돌린 뒤 결과에 따라 주문상태 변경
    public void compensateCreate(CreateOrderProgress progress) {
        UUID orderId = progress.getOrderId();
        log.info("{} 시작 (되돌릴 단계: {}) | orderId={}", CREATE, targetSteps(progress), orderId);

        boolean compensated = rollbackCreate(CREATE, progress);
        if (!markCreateFailed(orderId, compensated)) {
            return;
        }

        if (compensated) {
            log.info("{} 완료 → 주문 FAILED | orderId={}", CREATE, orderId);
        } else {
            log.error("{} 실패 → 주문 COMPENSATION_FAILED (재보상 대상) | orderId={}", CREATE, orderId);
        }
    }

    //COMPENSATION_FAILED 주문 재보상 / 모든 단계를 다시 보상한 뒤 결과 반영
    //재보상 성공 FAILED, 실패시 재보상 실패 횟수 증가 / 상한에 도달하면 수동 처리 대상
    public void recompensateCreate(OrderItem orderItem, int maxRetryCount) {
        CreateOrderProgress progress = CreateOrderProgress.forRecompensation(orderItem);
        UUID orderId = progress.getOrderId();
        log.info("{} 시작 (되돌릴 단계: {}) | orderId={} retryCount={}/{}", RECOMPENSATE, targetSteps(progress),
                orderId, orderItem.getOrder().getCompensationRetryCount(), maxRetryCount);

        boolean compensated = rollbackCreate(RECOMPENSATE, progress);

        int retryCount;
        try {
            retryCount = orderTransactionService.failRecompensation(orderId, compensated);
        } catch (RuntimeException e) {
            //상태가 COMPENSATION_FAILED로 남아 다음 재보상 대상이 됨 (보상 API는 멱등)
            log.error("{} 주문 상태 변경 실패 (다음 주기에 다시 재보상) | orderId={} compensated={}",
                    RECOMPENSATE, orderId, compensated, e);
            return;
        }

        if (compensated) {
            log.info("{} 완료 → 주문 FAILED | orderId={} retryCount={}/{}",
                    RECOMPENSATE, orderId, retryCount, maxRetryCount);
        } else if (retryCount >= maxRetryCount) {
            log.error("{} 실패 → 수동 처리 필요 (재시도 횟수 초과) | orderId={} retryCount={}/{} hubId={} itemId={} quantity={}",
                    RECOMPENSATE, orderId, retryCount, maxRetryCount,
                    progress.getHubId(), progress.getItemId(), progress.getQuantity());
        } else {
            log.warn("{} 실패 (다음 주기에 다시 재보상) | orderId={} retryCount={}/{}",
                    RECOMPENSATE, orderId, retryCount, maxRetryCount);
        }
    }

    //배송취소 실패시 주문상태 CONFIRMED로 복구
    public void compensateCancel(UUID orderId) {
        compensate(CANCEL, orderId, "주문 상태 복구", () -> orderTransactionService.revertCancel(orderId));
    }

    //배송삭제 실패시 주문/주문상품 삭제 복구
    public void compensateDelete(UUID orderId) {
        compensate(DELETE, orderId, "주문 삭제 복구", () -> orderTransactionService.revertDelete(orderId));
    }

    //되돌릴 단계 / 배송은 재고보다 먼저 되돌림
    private String targetSteps(CreateOrderProgress progress) {
        if (progress.isDeliveryCreateRequested()) {
            return "배송, 재고";
        }
        return progress.isStockDecreaseRequested() ? "재고" : "없음";
    }

    //하나가 실패해도 나머지 보상은 계속 수행, 모두 성공하면 true
    private boolean rollbackCreate(String tag, CreateOrderProgress progress) {
        UUID orderId = progress.getOrderId();
        boolean compensated = true;

        //배송생성을 요청했다면 생성 여부와 관계없이 주문ID로 취소/삭제 (배송이 없으면 이후 생성을 차단)
        if (progress.isDeliveryCreateRequested()) {
            compensated &= rollbackDelivery(tag, orderId);
        }
        if (progress.isStockDecreaseRequested()) {
            compensated &= rollbackStock(tag, progress);
        }
        return compensated;
    }

    //배송취소 후 삭제 / 배송은 취소된 상태에서만 삭제 가능하므로 취소 실패시 삭제 생략
    //이미 삭제된 배송(이전 보상에서 취소/삭제 완료)은 되돌릴 것이 없으므로 보상 성공
    private boolean rollbackDelivery(String tag, UUID orderId) {
        boolean notDeleted;
        try {
            notDeleted = clientService.cancelDeliveryIfNotDeleted(orderId);
        } catch (RuntimeException e) {
            log.error("{} 배송 취소 실패 | orderId={}", tag, orderId, e);
            return false;
        }

        if (!notDeleted) {
            log.info("{} 배송 취소 생략 (이미 삭제된 배송) | orderId={}", tag, orderId);
            return true;
        }
        log.info("{} 배송 취소 완료 | orderId={}", tag, orderId);
        return compensate(tag, orderId, "배송 삭제", () -> clientService.deleteDeliveryByOrderId(orderId));
    }

    //재고차감 응답(타임아웃 등)만으로는 차감 여부를 알 수 없어 같은 주문ID로 차감을 다시 요청해 확정
    //차감된 경우에만 복구, 차감된 적 없으면 되돌릴 것이 없으므로 보상 성공
    //재고복원의 409는 차감 기록 없음과 복구 불가를 구분할 수 없어 차감 확인으로 먼저 거름
    private boolean rollbackStock(String tag, CreateOrderProgress progress) {
        UUID orderId = progress.getOrderId();
        boolean decreased;
        try {
            decreased = clientService.confirmStockDecreased(
                    progress.getHubId(), progress.getItemId(), progress.getQuantity(), orderId);
        } catch (RuntimeException e) {
            log.error("{} 재고 차감 확인 실패 | orderId={}", tag, orderId, e);
            return false;
        }

        if (!decreased) {
            log.info("{} 재고 차감 확인 완료 (차감 안 됨) | orderId={}", tag, orderId);
            log.info("{} 재고 복구 생략 (차감된 재고 없음) | orderId={}", tag, orderId);
            return true;
        }
        log.info("{} 재고 차감 확인 완료 (차감됨) | orderId={}", tag, orderId);
        return compensate(tag, orderId, "재고 복구", () -> clientService.restoreStock(
                progress.getHubId(), progress.getItemId(), progress.getQuantity(), orderId));
    }

    //보상 실행 / 외부 호출은 Feign Retryer에서 재시도가 끝난 상태, DB 복구는 재시도하지 않으므로 실패하면 최종 실패
    private boolean compensate(String tag, UUID orderId, String action, Runnable compensation) {
        try {
            compensation.run();
            log.info("{} {} 완료 | orderId={}", tag, action, orderId);
            return true;
        } catch (RuntimeException e) {
            log.error("{} {} 실패 | orderId={}", tag, action, orderId, e);
            return false;
        }
    }

    //보상 성공 FAILED, 보상 실패 COMPENSATION_FAILED / 상태변경 실패는 로그만 남기고 false
    private boolean markCreateFailed(UUID orderId, boolean compensated) {
        try {
            orderTransactionService.failCreate(orderId, compensated);
            return true;
        } catch (RuntimeException e) {
            //WAITING으로 남은 주문은 스케줄러가 일정 시간 뒤 재보상 대상으로 전환
            log.error("{} 주문 상태 변경 실패 (WAITING 유지 → 스케줄러가 재보상 대상으로 전환) | orderId={} compensated={}",
                    CREATE, orderId, compensated, e);
            return false;
        }
    }
}

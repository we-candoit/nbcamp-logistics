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

    //주문생성 실패시 성공한 단계를 역순으로 되돌린 뒤 결과에 따라 주문상태 변경
    public void compensateCreate(CreateOrderProgress progress) {
        boolean compensated = rollbackCreate(progress);
        markCreateFailed(progress.getOrderId(), compensated);
    }

    //COMPENSATION_FAILED 주문 재보상 / 모든 단계를 다시 보상한 뒤 결과 반영
    //재보상 성공 FAILED, 실패시 재보상 실패 횟수 증가 / 상한에 도달하면 수동 처리 대상
    public void recompensateCreate(OrderItem orderItem, int maxRetryCount) {
        CreateOrderProgress progress = CreateOrderProgress.forRecompensation(orderItem);
        UUID orderId = progress.getOrderId();
        boolean compensated = rollbackCreate(progress);

        int retryCount;
        try {
            retryCount = orderTransactionService.failRecompensation(orderId, compensated);
        } catch (RuntimeException e) {
            //상태가 COMPENSATION_FAILED로 남아 다음 재보상 대상이 됨 (보상 API는 멱등)
            log.error("주문 재보상 결과 반영 실패 orderId={} compensated={}", orderId, compensated, e);
            return;
        }

        if (compensated) {
            log.info("주문 재보상 성공 orderId={} retryCount={}", orderId, retryCount);
        } else if (retryCount >= maxRetryCount) {
            log.error("주문 보상 수동 처리 필요 orderId={} retryCount={}/{} hubId={} itemId={} quantity={}",
                    orderId, retryCount, maxRetryCount,
                    progress.getHubId(), progress.getItemId(), progress.getQuantity());
        } else {
            log.warn("주문 재보상 실패 orderId={} retryCount={}/{}", orderId, retryCount, maxRetryCount);
        }
    }

    //배송취소 실패시 주문상태 CONFIRMED로 복구
    public void compensateCancel(UUID orderId) {
        compensate(orderId, "주문취소 복구", () -> orderTransactionService.revertCancel(orderId));
    }

    //배송삭제 실패시 주문/주문상품 삭제 복구
    public void compensateDelete(UUID orderId) {
        compensate(orderId, "주문삭제 복구", () -> orderTransactionService.revertDelete(orderId));
    }

    //하나가 실패해도 나머지 보상은 계속 수행, 모두 성공하면 true
    private boolean rollbackCreate(CreateOrderProgress progress) {
        UUID orderId = progress.getOrderId();
        boolean compensated = true;
        log.info("주문 보상 시작 orderId={} stockDecreaseRequested={} deliveryCreateRequested={}",
                orderId, progress.isStockDecreaseRequested(), progress.isDeliveryCreateRequested());

        //배송생성을 요청했다면 생성 여부와 관계없이 주문ID로 취소/삭제 (배송이 없으면 이후 생성을 차단)
        if (progress.isDeliveryCreateRequested()) {
            compensated &= rollbackDelivery(orderId);
        }
        if (progress.isStockDecreaseRequested()) {
            compensated &= rollbackStock(progress);
        }
        log.info("주문 보상 종료 orderId={} compensated={}", orderId, compensated);
        return compensated;
    }

    //배송취소 후 삭제 / 배송은 취소된 상태에서만 삭제 가능하므로 취소 실패시 삭제 생략
    //이미 삭제된 배송(이전 보상에서 취소/삭제 완료)은 되돌릴 것이 없으므로 보상 성공
    private boolean rollbackDelivery(UUID orderId) {
        boolean notDeleted;
        try {
            notDeleted = clientService.cancelDeliveryIfNotDeleted(orderId);
        } catch (RuntimeException e) {
            log.error("주문 보상 최종 실패 orderId={} action={}", orderId, "배송취소", e);
            return false;
        }

        if (!notDeleted) {
            log.info("이미 삭제된 배송이라 배송취소/삭제 생략 orderId={}", orderId);
            return true;
        }
        log.info("주문 보상 완료 orderId={} action={}", orderId, "배송취소");
        return compensate(orderId, "배송삭제", () -> clientService.deleteDeliveryByOrderId(orderId));
    }

    //재고차감 응답(타임아웃 등)만으로는 차감 여부를 알 수 없어 같은 주문ID로 차감을 다시 요청해 확정
    //차감된 경우에만 복구, 차감된 적 없으면 되돌릴 것이 없으므로 보상 성공
    //재고복원의 409는 차감 기록 없음과 복구 불가를 구분할 수 없어 차감 확인으로 먼저 거름
    private boolean rollbackStock(CreateOrderProgress progress) {
        UUID orderId = progress.getOrderId();
        boolean decreased;
        try {
            decreased = clientService.confirmStockDecreased(
                    progress.getHubId(), progress.getItemId(), progress.getQuantity(), orderId);
        } catch (RuntimeException e) {
            log.error("주문 보상 최종 실패 orderId={} action={}", orderId, "재고차감확인", e);
            return false;
        }

        if (!decreased) {
            log.info("차감된 재고가 없어 재고복구 생략 orderId={}", orderId);
            return true;
        }
        log.info("재고차감 확인됨, 재고복구 진행 orderId={}", orderId);
        return compensate(orderId, "재고복구", () -> clientService.restoreStock(
                progress.getHubId(), progress.getItemId(), progress.getQuantity(), orderId));
    }

    //보상 실행 / 외부 호출은 Feign Retryer에서 재시도가 끝난 상태, DB 복구는 재시도하지 않으므로 실패하면 최종 실패
    private boolean compensate(UUID orderId, String action, Runnable compensation) {
        try {
            compensation.run();
            log.info("주문 보상 완료 orderId={} action={}", orderId, action);
            return true;
        } catch (RuntimeException e) {
            log.error("주문 보상 최종 실패 orderId={} action={}", orderId, action, e);
            return false;
        }
    }

    //보상 성공 FAILED, 보상 실패 COMPENSATION_FAILED, 상태변경 실패는 로그만 남김
    private void markCreateFailed(UUID orderId, boolean compensated) {
        try {
            orderTransactionService.failCreate(orderId, compensated);
        } catch (RuntimeException e) {
            log.error("주문생성 실패 상태변경 실패 orderId={} compensated={}", orderId, compensated, e);
        }
    }
}

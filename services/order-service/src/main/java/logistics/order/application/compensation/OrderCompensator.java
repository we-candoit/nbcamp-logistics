package logistics.order.application.compensation;

import logistics.order.application.service.ClientService;
import logistics.order.application.service.OrderTransactionService;
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

        //배송생성을 요청했다면 생성 여부와 관계없이 주문ID로 취소/삭제 (배송이 없으면 이후 생성을 차단)
        if (progress.isDeliveryRequested()) {
            //배송은 취소된 상태에서만 삭제 가능
            compensated &= compensate(orderId, "배송취소", () -> clientService.cancelDeliveryByOrderId(orderId))
                    && compensate(orderId, "배송삭제", () -> clientService.deleteDeliveryByOrderId(orderId));
        }
        if (progress.isStockDecreased()) {
            compensated &= compensate(orderId, "재고복구", () -> clientService.restoreStock(
                    progress.getHubId(), progress.getItemId(), progress.getQuantity()));
        }
        return compensated;
    }

    //보상 실행 / 외부 호출은 Feign Retryer에서 재시도가 끝난 상태, DB 복구는 재시도하지 않으므로 실패하면 최종 실패
    private boolean compensate(UUID orderId, String action, Runnable compensation) {
        try {
            compensation.run();
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

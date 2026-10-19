package logistics.order.application.service;

import feign.FeignException;
import logistics.order.application.validator.OrderValidator;
import logistics.order.infrastructure.client.DeliveryServiceClient;
import logistics.order.infrastructure.client.dto.CancelDeliveryResponse;
import logistics.order.common.exception.CustomException;
import logistics.order.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final DeliveryServiceClient deliveryServiceClient;
    private final OrderTransactionService orderTransactionService;
    private final OrderValidator orderValidator;

    //배송취소 요청 / 배송이 취소되지 않으면 주문도 취소 불가
    public void cancelDelivery(UUID deliveryId, UUID orderId) {
        try {
            CancelDeliveryResponse response = deliveryServiceClient.cancelDelivery(deliveryId);
            orderValidator.validateDeliveryCanceled(response);
        } catch (RuntimeException e) {
            orderTransactionService.compensateCancelOrder(orderId);
            //배송 서비스가 4xx로 응답 (409 이미 출발한 배송, 404 배송 없음 등) → 취소 불가
            if (e instanceof FeignException fe && fe.status() >= 400 && fe.status() < 500) {
                throw new CustomException(ErrorCode.C409_ORDER_NOT_CANCELABLE);
            }
            //응답 없음(-1), 5xx는 전역 처리기로 전달
            throw e;
        }
    }
}

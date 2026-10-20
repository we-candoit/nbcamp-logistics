package logistics.delivery.infrastructure.client;

import feign.FeignException;
import logistics.delivery.common.exception.DeliveryErrorCode;
import logistics.delivery.common.exception.DeliveryException;
import logistics.delivery.infrastructure.client.dto.OrderDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderClientAdapter {
    private final OrderClient orderClient;

    /**
     * 원 요청자의 권한으로 주문을 조회한다. 조회 거절 시 배송 존재 여부를 숨긴다.
     *
     * @param orderId 배송에 연결된 주문 ID
     * @param requesterId 원 요청자의 사용자 ID
     * @param role 원 요청자의 역할
     * @return 주문 ID와 주문자 ID가 확인된 응답
     * @throws DeliveryException 조회 거절, 호출 실패 또는 잘못된 응답인 경우
     */
    public OrderDetailResponse getOrderForDeliveryAccess(UUID orderId, UUID requesterId, String role) {
        OrderDetailResponse order;
        try {
            order = orderClient.getOrderDetail(orderId, requesterId, role);
        } catch (FeignException exception) {
            if (exception.status() == 403 || exception.status() == 404) {
                throw new DeliveryException(DeliveryErrorCode.DELIVERY_NOT_FOUND, exception);
            }
            throw new DeliveryException(DeliveryErrorCode.ORDER_SERVICE_UNAVAILABLE, exception);
        }

        if (order == null || order.orderUserId() == null || !orderId.equals(order.orderId())) {
            throw new DeliveryException(DeliveryErrorCode.INVALID_ORDER_SERVICE_RESPONSE);
        }
        return order;
    }
}

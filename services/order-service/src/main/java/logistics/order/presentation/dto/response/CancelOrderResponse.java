package logistics.order.presentation.dto.response;

import logistics.order.domain.entity.Order;
import logistics.order.domain.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor @NoArgsConstructor
public class CancelOrderResponse {

    private UUID orderId;
    private UUID deliveryId;
    private OrderStatus status;

    public static CancelOrderResponse from(Order order) {
        return new CancelOrderResponse(order.getId(), order.getDeliveryId(), order.getOrderStatus());
    }
}

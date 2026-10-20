package logistics.order.presentation.dto.response;

import logistics.order.domain.entity.Order;
import logistics.order.domain.entity.OrderItem;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor @NoArgsConstructor
public class CreateOrderResponse {

    private UUID orderId;
    private UUID itemId;
    private UUID deliveryId;
    private LocalDateTime createdAt;

    public static CreateOrderResponse from(OrderItem orderItem) {
        Order order = orderItem.getOrder();

        return new CreateOrderResponse(
                order.getId(),
                orderItem.getItemId(),
                order.getDeliveryId(),
                order.getCreatedAt()
        );
    }
}

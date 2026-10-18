package logistics.order.presentation.dto.response;

import logistics.order.domain.entity.Order;
import logistics.order.domain.entity.OrderItem;
import logistics.order.domain.enums.OrderItemStatus;
import logistics.order.domain.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor @NoArgsConstructor
public class GetOrderByIdResponse {

    private UUID orderId;
    private UUID deliveryId;
    private UUID orderUserId;
    private UUID requesterId;
    private OrderStatus status;
    private String address;
    private String requestNote;
    private Item item;
    private LocalDateTime createdAt;

    @Data
    @AllArgsConstructor @NoArgsConstructor
    public static class Item {
        private UUID itemId;
        private UUID supplierId;
        private String itemName;
        private Long price;
        private Long quantity;
        private Long totalPrice;
    }

    public static GetOrderByIdResponse from(OrderItem orderItem) {
        Order order = orderItem.getOrder();

        return new GetOrderByIdResponse(
                order.getId(),
                order.getDeliveryId(),
                order.getOrderUserId(),
                order.getRequesterId(),
                order.getOrderStatus(),
                order.getAddress(),
                order.getRequestNote(),

                new Item(
                        orderItem.getItemId(),
                        orderItem.getSupplierId(),
                        orderItem.getItemName(),
                        orderItem.getPrice(),
                        orderItem.getQuantity(),
                        orderItem.getPrice() * orderItem.getQuantity()
                ),
                order.getCreatedAt()
        );
    }
}

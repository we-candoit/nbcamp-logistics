package logistics.order.infrastructure.client.dto;

import logistics.order.domain.entity.Order;
import logistics.order.domain.entity.OrderItem;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor @NoArgsConstructor
public class CreateDeliveryRequest {

    private UUID orderId;
    private UUID departureHubId;
    private UUID destinationHubId;
    private String address;
    private String recipientName;
    private String recipientPhone;

    //배송생성 API Request 빌드 / 출발허브는 주문상품, 도착허브와 주소는 주문 기준
    public static CreateDeliveryRequest of(Order order, OrderItem orderItem,
                                           String recipientName, String recipientPhone) {
        return new CreateDeliveryRequest(
                order.getId(),
                orderItem.getDepartureHubId(),
                order.getDestinationHubId(),
                order.getAddress(),
                recipientName,
                recipientPhone
        );
    }
}

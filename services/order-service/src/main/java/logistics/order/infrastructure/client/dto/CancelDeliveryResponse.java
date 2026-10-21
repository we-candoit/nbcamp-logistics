package logistics.order.infrastructure.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

//주문ID로 배송취소 응답 / 배송이 없으면 deliveryId는 null
@Data
@AllArgsConstructor @NoArgsConstructor
public class CancelDeliveryResponse {

    private UUID orderId;
    private UUID deliveryId;
    private String result;

}

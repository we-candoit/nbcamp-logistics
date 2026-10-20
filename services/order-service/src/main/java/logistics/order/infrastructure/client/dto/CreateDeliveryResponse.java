package logistics.order.infrastructure.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor @NoArgsConstructor
public class CreateDeliveryResponse {

    private UUID deliveryId;
    private UUID orderId;
    private String status;

}

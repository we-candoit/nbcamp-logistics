package logistics.order.infrastructure.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor @NoArgsConstructor
public class CancelDeliveryResponse {

    private UUID deliveryId;
    private String status;

}

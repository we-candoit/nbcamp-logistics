package logistics.order.infrastructure.client.dto;

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

}

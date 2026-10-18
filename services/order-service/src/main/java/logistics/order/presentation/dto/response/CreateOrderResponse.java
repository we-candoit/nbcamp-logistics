package logistics.order.presentation.dto.response;

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

}

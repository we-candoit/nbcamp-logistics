package logistics.order.infrastructure.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor @NoArgsConstructor
public class StockResponse {
    private UUID id;
    private UUID hubId;
    private UUID productId;
    private Long quantity;
}

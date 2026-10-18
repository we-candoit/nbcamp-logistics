package logistics.order.infrastructure.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor @NoArgsConstructor
public class FetchItemByIdResponse {

    private UUID itemId;
    private UUID companyId;
    private String companyName;
    private String name;
    private Long price;
    private String description;
    private List<Stock> stocks;
    private Long totalQuantity;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @AllArgsConstructor @NoArgsConstructor
    public static class Stock {
        private UUID hubId;
        private Long quantity;
    }
}

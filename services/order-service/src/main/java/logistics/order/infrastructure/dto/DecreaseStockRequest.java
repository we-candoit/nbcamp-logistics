package logistics.order.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@AllArgsConstructor @NoArgsConstructor
public class DecreaseStockRequest {
    private List<Hub> hubs;

    @Data
    @AllArgsConstructor @NoArgsConstructor
    public static class Hub {
        private UUID hubId;
        private List<Item> items;
    }

    @Data
    @AllArgsConstructor @NoArgsConstructor
    public static class Item {
        private UUID itemID;
        private Long quantity;
    }
}
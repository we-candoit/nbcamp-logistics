package logistics.order.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor @NoArgsConstructor
public class FetchItemsByIdsResponse {
    List<Item> items;
    List<UUID> notFoundIds;

    @Data
    @AllArgsConstructor @NoArgsConstructor
    public static class Item {
        private UUID itemId;
        private UUID hubId;
        private UUID managerUserId;
        private UUID companyId;
        private Long price;
        private String address;
    }
}

package logistics.order.infrastructure.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor @NoArgsConstructor
public class FetchItemByIdResponse {

    private UUID itemId;
    private UUID hubId;
    private UUID managerUserId;
    private UUID companyId;
    private Long price;
    private String address;

}

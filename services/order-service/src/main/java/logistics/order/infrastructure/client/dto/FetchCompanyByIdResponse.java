package logistics.order.infrastructure.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor @AllArgsConstructor
public class FetchCompanyByIdResponse {

    private UUID companyId;
    private UUID hubId;
    private UUID managerUserId;
    private String name;
    private String address;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
package logistics.order.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor @AllArgsConstructor
public class GetCompanyResponse {
    private UUID companyId;
    private UUID managerUserId;
    private UUID hubId;
    private String name;
    private String address;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
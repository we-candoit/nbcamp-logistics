package logistics.company.infrastructure.client.dto;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HubResponse {
    private UUID hubId;
    private String name;
    private UUID managerUserId;
}

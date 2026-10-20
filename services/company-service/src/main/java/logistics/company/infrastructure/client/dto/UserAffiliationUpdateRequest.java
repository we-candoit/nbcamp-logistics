package logistics.company.infrastructure.client.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UserAffiliationUpdateRequest {
    private UUID companyId;
    private String companyName;

    public static UserAffiliationUpdateRequest confirm(UUID companyId, String companyName) {
        return new UserAffiliationUpdateRequest(companyId, companyName);
    }
}

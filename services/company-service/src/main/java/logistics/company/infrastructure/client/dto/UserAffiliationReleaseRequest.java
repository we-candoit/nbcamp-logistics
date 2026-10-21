package logistics.company.infrastructure.client.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UserAffiliationReleaseRequest {
    private UUID companyId;     // 해제 대상 업체 (user-service가 현재 소속과 일치하는지 확인)

    public static UserAffiliationReleaseRequest of(UUID companyId) {
        return new UserAffiliationReleaseRequest(companyId);
    }
}
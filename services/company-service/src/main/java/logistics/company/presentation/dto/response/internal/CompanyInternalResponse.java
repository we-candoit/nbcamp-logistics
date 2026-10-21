package logistics.company.presentation.dto.response.internal;

import logistics.company.domain.entity.Company;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CompanyInternalResponse {

    private UUID companyId;                     // 업체 id
    private UUID hubId;                         // 업체 관리 허브 id
    private UUID managerUserId;                 // 업체 관리자 id
    private String name;                        // 업체명
    private String address;                     // 업체 주소

    public static CompanyInternalResponse from(Company company) {
        return new CompanyInternalResponse(
                company.getId(),
                company.getHubId(),
                company.getManagerUserId(),
                company.getName(),
                company.getAddress()
        );
    }
}
package logistics.company.presentation.dto.response;

import logistics.company.domain.entity.Company;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CompanyResponse {

    private UUID companyId;                     // 업체 id
    private UUID hubId;                         // 소속 허브 id
    private UUID managerUserId;                 // 업체 관리자 id
    private String name;                        // 업체명
    private String address;                     // 업체 주소

    private LocalDateTime createdAt;            // 생성 시각
    private LocalDateTime updatedAt;            // 수정 시각

    public static CompanyResponse of(Company company) {
        return new CompanyResponse(
                company.getId(),
                company.getHubId(),
                company.getManagerUserId(),
                company.getName(),
                company.getAddress(),
                company.getCreatedAt(),
                company.getUpdatedAt()
        );
    }
}

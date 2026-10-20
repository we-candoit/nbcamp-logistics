package logistics.company.presentation.dto.response.internal;

import logistics.company.domain.entity.CompanyItem;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CompanyItemInternalResponse {
    private UUID itemId;                        // 상품 id
    private UUID hubId;                         // 소속 업체 관리 허브 id
    private Long price;                         // 상품 가격
    private String address;                     // 소속 업체 주소
    private UUID managerUserId;                 // 소속 업체 관리자 id

    public static CompanyItemInternalResponse from(CompanyItem item) {
        return new CompanyItemInternalResponse(
                item.getId(),
                item.getCompany().getHubId(),
                item.getPrice(),
                item.getCompany().getAddress(),
                item.getCompany().getManagerUserId()
        );
    }
}

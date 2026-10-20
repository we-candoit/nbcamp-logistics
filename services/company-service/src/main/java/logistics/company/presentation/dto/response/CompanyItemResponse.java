package logistics.company.presentation.dto.response;

import logistics.company.domain.entity.CompanyItem;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CompanyItemResponse {
    private UUID itemId;                        // 상품 id
    private UUID companyId;                     // 소속 업체 id
    private String companyName;                 // 소속 업체명
    private String name;                        // 상품명
    private Long price;                         // 상품 가격
    private String description;                 // 상품 설명

    private LocalDateTime createdAt;            // 생성 시각
    private LocalDateTime updatedAt;            // 수정 시각

    public static CompanyItemResponse from(CompanyItem item) {
        return new CompanyItemResponse(
                item.getId(),
                item.getCompany().getId(),
                item.getCompany().getName(),
                item.getName(),
                item.getPrice(),
                item.getDescription(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}

package logistics.company.presentation.dto.request.companyItem;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class CompanyItemSearchCondition {

    private String itemName;                    // 상품명 (부분 일치)

    private UUID itemId;                        // 상품 id (일치)

    private UUID companyId;                     // 업체 id (일치)

    private String companyName;                 // 업체명 (부분 일치)

    private String description;                 // 상품 설명 (부분 일치)

    private Long minPrice;                       // 최소 가격

    private Long maxPrice;                       // 최대 가격

    // 최소 가격이 최대 가격보다 크면 잘못된 범위
    public boolean invalidPriceRange() {
        return minPrice != null && maxPrice != null && minPrice > maxPrice;
    }
}
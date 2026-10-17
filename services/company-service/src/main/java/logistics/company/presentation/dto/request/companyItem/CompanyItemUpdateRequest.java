package logistics.company.presentation.dto.request.companyItem;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanyItemUpdateRequest {

    @Pattern(regexp = ".*\\S.*", message = "상품명은 공백만 입력할 수 없습니다.")
    @Size(max = 100, message = "상품명은 100자 이하여야 합니다.")
    private String name;                        // 상품명

    @Min(value = 10, message = "상품 가격은 10 이상이어야 합니다.")
    private Long price;                         // 상품 가격

    @Size(max = 255, message = "상품 설명은 255자 이하여야 합니다.")
    private String description;                 // 상품 설명
}
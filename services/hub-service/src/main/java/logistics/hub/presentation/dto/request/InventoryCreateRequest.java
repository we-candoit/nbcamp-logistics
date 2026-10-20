package logistics.hub.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class InventoryCreateRequest {

    @NotNull(message = "업체 ID는 필수입니다.")
    private UUID companyId;

    @NotNull(message = "상품 ID는 필수입니다.")
    private UUID productId;

    @NotNull(message = "재고 수량은 필수입니다.")
    @PositiveOrZero(message = "재고 수량은 0 이상이어야 합니다.")
    private Integer quantity;
}
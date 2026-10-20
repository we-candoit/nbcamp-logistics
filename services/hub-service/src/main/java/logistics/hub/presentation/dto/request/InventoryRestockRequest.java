package logistics.hub.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class InventoryRestockRequest {

    @NotNull(message = "입고 요청 ID는 필수입니다.")
    private UUID restockRequestId;

    @NotNull(message = "업체 ID는 필수입니다.")
    private UUID companyId;

    @NotNull(message = "입고 수량은 필수입니다.")
    @Positive(message = "입고 수량은 1 이상이어야 합니다.")
    private Integer quantity;
}
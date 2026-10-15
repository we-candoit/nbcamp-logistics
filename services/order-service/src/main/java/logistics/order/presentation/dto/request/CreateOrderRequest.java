package logistics.order.presentation.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor @AllArgsConstructor
public class CreateOrderRequest {

    @NotNull(message = "요청 업체 아이디는 필수입니다.")
    private UUID requesterId;

    @NotNull(message = "공급 업체 아이디는 필수입니다.")
    private UUID supplierId;

    @Size(max = 100, message = "요청사항은 100자 이하입니다.")
    private String requestNote;

    @NotEmpty(message = "주문 당 상품수는 1개 이상이어야 합니다.")
    @Size(max = 100, message = "주문 당 상품수는 100개 이하이어야 합니다.")
    private List<@NotNull(message = "주문상품 정보는 필수입니다.") @Valid Item> items;


    @Getter
    @NoArgsConstructor @AllArgsConstructor
    public static class Item {

        @NotNull(message = "상품 아이디는 필수입니다.")
        private UUID itemId;

        @NotBlank(message = "상품명은 필수 입니다.")
        @Size(max = 100, message = "상품명은 1자 이상 100자 이하입니다.")
        private String itemName;

        @Positive(message = "상품 수량은 1개 이상이어야 합니다.")
        @Max(value = 100, message = "상품 수량은 100개 이하이어야 합니다.")
        @NotNull(message = "상품 수량은 필수입니다.")
        private Long quantity;

        @PositiveOrZero(message = "상품 가격은 0 이상이어야 합니다.")
        @Max(value = 1_000_000_000, message = "상품 가격은 10억 이하이어야 합니다.")
        @NotNull(message = "상품 가격은 필수입니다.")
        private Long price;
    }
}
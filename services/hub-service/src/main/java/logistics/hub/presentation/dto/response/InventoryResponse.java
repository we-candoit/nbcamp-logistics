package logistics.hub.presentation.dto.response;

import logistics.hub.domain.entity.Inventory;
import lombok.Getter;

import java.util.UUID;

@Getter
public class InventoryResponse {

    private final UUID id;
    private final UUID hubId;
    private final UUID productId;
    private final Integer quantity;

    public InventoryResponse(Inventory inventory) {
        this(inventory, inventory.getQuantity());
    }

    // 재시도 응답에는 입고 직후 수량을 사용한다.
    public InventoryResponse(Inventory inventory, Integer quantity) {
        this.id = inventory.getId();
        this.hubId = inventory.getHub().getId();
        this.productId = inventory.getProductId();
        this.quantity = quantity;
    }
}
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
        this.id = inventory.getId();
        this.hubId = inventory.getHub().getId();
        this.productId = inventory.getProductId();
        this.quantity = inventory.getQuantity();
    }
}
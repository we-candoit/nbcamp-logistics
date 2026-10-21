package logistics.hub.domain.entity;

import jakarta.persistence.*;
import logistics.hub.common.entity.BaseEntity;
import logistics.hub.domain.enums.InventoryMovementType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Getter
@Table(name = "p_inventory_movements", uniqueConstraints = {@UniqueConstraint(name = "uk_inventory_movements_inventory_order_type", columnNames = {"inventory_id", "order_id", "movement_type"})})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryMovement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_id", nullable = false, updatable = false)
    private Inventory inventory;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, updatable = false, length = 20)
    private InventoryMovementType type;

    @Column(name = "quantity", nullable = false, updatable = false)
    private Integer quantity;

    @Column(name = "result_quantity", nullable = false, updatable = false)
    private Integer resultQuantity;

    public InventoryMovement(
            Inventory inventory,
            UUID orderId,
            InventoryMovementType type,
            Integer quantity,
            UUID userId
    ) {
        super(userId);
        this.inventory = inventory;
        this.orderId = orderId;
        this.type = type;
        this.quantity = quantity;
        this.resultQuantity = inventory.getQuantity();
    }

    public boolean matches(Integer quantity) {
        return this.quantity.equals(quantity);
    }
}

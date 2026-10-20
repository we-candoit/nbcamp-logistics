package logistics.hub.domain.entity;

import jakarta.persistence.*;
import logistics.hub.common.entity.BaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Getter
@Table(name = "p_inventory_restocks", uniqueConstraints = {@UniqueConstraint(name = "uk_inventory_restocks_inventory_request", columnNames = {"inventory_id", "request_id"})})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryRestock extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_id", nullable = false, updatable = false)
    private Inventory inventory;

    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "quantity", nullable = false, updatable = false)
    private Integer quantity;

    @Column(name = "result_quantity", nullable = false, updatable = false)
    private Integer resultQuantity;

    public InventoryRestock(
            Inventory inventory,
            UUID requestId,
            UUID companyId,
            Integer quantity,
            UUID userId
    ) {
        super(userId);
        this.inventory = inventory;
        this.requestId = requestId;
        this.companyId = companyId;
        this.quantity = quantity;
        this.resultQuantity = inventory.getQuantity();
    }

    public boolean matches(
            UUID companyId,
            Integer quantity,
            UUID userId
    ) {
        return this.companyId.equals(companyId) && this.quantity.equals(quantity) && getCreatedBy().equals(userId);
    }
}

package logistics.hub.domain.entity;

import jakarta.persistence.*;
import logistics.hub.common.entity.BaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Getter
@Table(name = "p_hub_items", uniqueConstraints = {
        @UniqueConstraint(name = "uk_hub_items_hub_product", columnNames = {"hub_id", "product_id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inventory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hub_id", nullable = false)
    private Hub hub;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    public Inventory(Hub hub, UUID productId, Integer quantity, UUID createdBy) {
        super(createdBy);

        if (hub == null) {
            throw new IllegalArgumentException("허브는 필수입니다.");
        }
        if (productId == null) {
            throw new IllegalArgumentException("상품 ID는 필수입니다.");
        }
        if (quantity == null || quantity < 0) {
            throw new IllegalArgumentException("재고 수량은 0 이상이어야 합니다.");
        }

        this.hub = hub;
        this.productId = productId;
        this.quantity = quantity;
    }

    public void restock(Integer amount, UUID userId) {
        validatePositiveAmount(amount);
        ensureNoOverflow(amount);
        this.quantity += amount;
        changeUpdatedBy(userId);
    }

    public void deduct(Integer amount, UUID userId) {
        validatePositiveAmount(amount);
        if (this.quantity < amount) {
            throw new IllegalStateException("재고가 부족합니다.");
        }
        this.quantity -= amount;
        changeUpdatedBy(userId);
    }

    public void restore(Integer amount, UUID userId) {
        validatePositiveAmount(amount);
        ensureNoOverflow(amount);
        this.quantity += amount;
        changeUpdatedBy(userId);
    }

    private void validatePositiveAmount(Integer amount) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
        }
    }

    private void ensureNoOverflow(Integer amount) {
        if (amount > Integer.MAX_VALUE - this.quantity) {
            throw new IllegalStateException("재고 수량의 최대값을 초과할 수 없습니다.");
        }
    }
}

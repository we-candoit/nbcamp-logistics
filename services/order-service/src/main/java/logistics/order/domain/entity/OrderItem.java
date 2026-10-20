package logistics.order.domain.entity;


import jakarta.persistence.*;
import logistics.order.common.entity.BaseEntity;
import logistics.order.common.exception.CustomException;
import logistics.order.common.exception.ErrorCode;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity @Getter
@Table(name = "p_order_item")
@NoArgsConstructor
public class OrderItem extends BaseEntity {

    @Id
    private final UUID id = UUID.randomUUID();

    @Column(nullable = false, updatable = false)
    private UUID itemId;
    
    @Column(length = 100, nullable = false, updatable = false)
    private String itemName;

    @Column(nullable = false, updatable = false)
    private Long price;

    @Column(nullable = false)
    private Long quantity;
    
    @Column(nullable = false, updatable = false)
    private UUID departureHubId;
    
    @Column(nullable = false, updatable = false)
    private UUID supplierId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Builder
    public OrderItem(UUID itemId, String itemName, Long price, Long quantity,
                     UUID departureHubId, UUID supplierId, Order order) {
        this.itemId = itemId;
        this.itemName = itemName;
        this.price = price;
        this.quantity = quantity;
        this.departureHubId = departureHubId;
        this.supplierId = supplierId;
        this.order = order;
    }

    //주문삭제, CANCELLED 상태에서만 가능
    public void delete(UUID userId) {
        if (!this.order.isDeletable()) {
            throw new CustomException(ErrorCode.C409_INVALID_ORDER_STATUS);
        }

        softDelete(userId);
    }
}

package logistics.order.domain.entity;


import jakarta.persistence.*;
import logistics.order.common.entity.BaseEntity;
import logistics.order.domain.enums.OrderItemStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity @Getter @Setter
@Table(name = "p_order_item")
@NoArgsConstructor
public class OrderItem extends BaseEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private OrderItemStatus orderItemStatus = OrderItemStatus.ORDERED;

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
}

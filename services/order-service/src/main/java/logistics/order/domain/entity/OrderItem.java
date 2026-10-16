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

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
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
    private UUID arrivalHubId;
    
    @Column(nullable = false, updatable = false)
    private UUID supplierId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    public OrderItem(OrderItemStatus orderItemStatus, UUID itemId, String itemName, Long price, Long quantity, UUID arrivalHubId, UUID supplierId, Order order) {
        this.orderItemStatus = orderItemStatus;
        this.itemId = itemId;
        this.itemName = itemName;
        this.price = price;
        this.quantity = quantity;
        this.arrivalHubId = arrivalHubId;
        this.supplierId = supplierId;
        this.order = order;
    }
}

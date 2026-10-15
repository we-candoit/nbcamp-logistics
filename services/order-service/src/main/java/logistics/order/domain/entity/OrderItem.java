package logistics.order.domain.entity;


import jakarta.persistence.*;
import logistics.order.common.entity.BaseEntity;
import logistics.order.domain.enums.OrderItemStatus;
import logistics.order.domain.entity.Order;

import java.util.UUID;

@Entity
@Table(name = "p_order_item")
public class OrderItem extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private OrderItemStatus orderItemStatus = OrderItemStatus.WAITING;

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
}

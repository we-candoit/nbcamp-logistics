package logistics.order.domain.entity;


import jakarta.persistence.*;
import logistics.order.common.entity.BaseEntity;

import java.util.UUID;

@Entity
@Table(name = "p_order_item")
public class OrderItem extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID itemId;

    @Column(nullable = false, length = 100)
    private String itemName;

    @Column(nullable = false)
    private Long price;

    @Column(nullable = false)
    private Long quantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;
}

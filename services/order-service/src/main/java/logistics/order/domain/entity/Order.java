package logistics.order.domain.entity;

import jakarta.persistence.*;
import logistics.order.common.entity.BaseEntity;
import logistics.order.domain.enums.OrderStatus;

import java.util.UUID;

@Entity
@Table(name = "p_order")
public class Order extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID orderUserId;

    @Column(nullable = false, updatable = false)
    private UUID requesterId;

    @Column(nullable = false, updatable = false)
    private UUID supplierId;

    @Column(nullable = false, updatable = false)
    private UUID arrivalHubId;

    @Column(nullable = false)
    private UUID departureHubId;

    @Column(nullable = false)
    private Long quantity;
    private String requestNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus orderStatus = OrderStatus.WAITING;
}
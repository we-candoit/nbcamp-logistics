package logistics.order.domain.entity;

import jakarta.persistence.*;
import logistics.order.common.entity.BaseEntity;
import logistics.order.domain.enums.OrderStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity @Getter @Setter
@Table(name = "p_order")
@NoArgsConstructor
public class Order extends BaseEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, updatable = false)
    private UUID orderUserId;

    @Column(nullable = false, updatable = false)
    private UUID deliveryId;

    @Column(nullable = false, updatable = false)
    private UUID requesterId;

    @Column(nullable = false, updatable = false)
    private UUID destinationHubId;

    @Column(nullable = false, updatable = false)
    private String address;
    private String requestNote;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private OrderStatus orderStatus = OrderStatus.WAITING;


    public Order(UUID orderUserId, UUID requesterId, UUID destinationHubId, String address, String requestNote) {
        this.orderUserId = orderUserId;
        this.requesterId = requesterId;
        this.destinationHubId = destinationHubId;
        this.address = address;
        this.requestNote = requestNote;
    }

    public void confirm(UUID deliveryId) {
        this.deliveryId = deliveryId;
        this.orderStatus = OrderStatus.CONFIRMED;
    }
}
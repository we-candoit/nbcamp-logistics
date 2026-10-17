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

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID orderUserId;

    @Column(nullable = false, updatable = false)
    private UUID requesterId;

    @Column(nullable = false, updatable = false)
    private UUID departureHubId;

    @Column(nullable = false, updatable = false)
    private String address;
    private String requestNote;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private OrderStatus orderStatus = OrderStatus.WAITING;


    //배송과 N:1 관계시 삭제 필요
    @Column
    private UUID deliveryId;

    public Order(UUID orderUserId, UUID requesterId, UUID departureHubId, String address, String requestNote) {
        this.orderUserId = orderUserId;
        this.requesterId = requesterId;
        this.departureHubId = departureHubId;
        this.address = address;
        this.requestNote = requestNote;
    }
}
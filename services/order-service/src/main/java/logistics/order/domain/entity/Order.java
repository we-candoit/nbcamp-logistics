package logistics.order.domain.entity;

import jakarta.persistence.*;
import logistics.order.common.entity.BaseEntity;
import logistics.order.domain.enums.OrderStatus;
import logistics.order.presentation.exception.CustomException;
import logistics.order.presentation.exception.ErrorCode;
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

    //주문확정, WAITING 상태에서만 가능
    public void confirm(UUID deliveryId) {
        //배송생성 응답에 배송ID가 없는 경우 서버측 문제
        if (deliveryId == null) {
            throw new CustomException(ErrorCode.C500_INTERNAL_SERVER_ERROR);
        }

        if (this.orderStatus != OrderStatus.WAITING) {
            throw new CustomException(ErrorCode.C409_INVALID_ORDER_STATUS);
        }

        this.deliveryId = deliveryId;
        this.orderStatus = OrderStatus.CONFIRMED;
    }
}
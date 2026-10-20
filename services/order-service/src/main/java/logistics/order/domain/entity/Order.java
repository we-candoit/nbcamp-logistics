package logistics.order.domain.entity;

import jakarta.persistence.*;
import logistics.order.common.entity.BaseEntity;
import logistics.order.domain.enums.OrderStatus;
import logistics.order.common.exception.CustomException;
import logistics.order.common.exception.ErrorCode;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity @Getter
@Table(name = "p_orders")
@NoArgsConstructor
public class Order extends BaseEntity {

    @Id
    private final UUID id = UUID.randomUUID();

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
    private OrderStatus status = OrderStatus.WAITING;


    @Builder
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

        if (this.status != OrderStatus.WAITING) {
            throw new CustomException(ErrorCode.C409_INVALID_ORDER_STATUS);
        }

        this.deliveryId = deliveryId;
        this.status = OrderStatus.CONFIRMED;
    }

    //주문취소 가능 여부
    public boolean isCancelable() {
        return this.status == OrderStatus.CONFIRMED;
    }

    //주문취소, CONFIRMED 상태에서만 가능
    public void cancel() {
        if (!isCancelable()) {
            throw new CustomException(ErrorCode.C409_INVALID_ORDER_STATUS);
        }

        this.status = OrderStatus.CANCELLED;
    }

    //주문삭제 가능 여부 / 취소된(CANCELLED) 주문만 가능 (배송 서비스도 취소된 배송만 삭제 허용)
    public boolean isDeletable() {
        return this.status == OrderStatus.CANCELLED;
    }

    //주문삭제, CANCELLED 상태에서만 가능
    public void delete(UUID userId) {
        if (!isDeletable()) {
            throw new CustomException(ErrorCode.C409_INVALID_ORDER_STATUS);
        }

        softDelete(userId);
    }

    //주문취소 보상, CANCELLED 상태만 CONFIRMED로 복구
    //보상 중 예외가 발생하면 원래 실패 원인이 가려지므로 예외 없이 무시
    public void revertCancel() {
        if (this.status == OrderStatus.CANCELLED) {
            this.status = OrderStatus.CONFIRMED;
        }
    }

    //주문생성 실패, 보상이 모두 성공한 경우 WAITING 상태만 FAILED로 변경
    public void failed() {
        if (OrderStatus.WAITING == this.status) {
            this.status = OrderStatus.FAILED;
        }
    }

    //주문생성 실패, 보상이 실패한 경우 WAITING 상태만 COMPENSATION_FAILED로 변경 / 수동 처리 대상
    public void compensationFailed() {
        if (OrderStatus.WAITING == this.status) {
            this.status = OrderStatus.COMPENSATION_FAILED;
        }
    }
}
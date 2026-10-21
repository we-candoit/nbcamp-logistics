package logistics.delivery.domain.entity;

import jakarta.persistence.*;
import logistics.delivery.common.entity.BaseEntity;
import logistics.delivery.common.exception.DeliveryErrorCode;
import logistics.delivery.common.exception.DeliveryException;
import logistics.delivery.domain.enums.DeliveryOrderControlStatus;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "p_delivery_order_controls")
public class DeliveryOrderControl extends BaseEntity {

    @Column(nullable = false, unique = true)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DeliveryOrderControlStatus status = DeliveryOrderControlStatus.ALLOWED;

    // 주문별 관리 기록을 생성
    public static DeliveryOrderControl create(UUID orderId) {
        DeliveryOrderControl control = new DeliveryOrderControl();
        control.orderId = orderId;
        return control;
    }

    // 취소, 삭제 처리로 배송 생성이 차단됐는지 확인
    public boolean isCreationBlocked() {
        return status != DeliveryOrderControlStatus.ALLOWED;
    }

    // 배송 취소 요청을 기록하고 배송 생성 차단
    public void markCancelled() {
        if (status == DeliveryOrderControlStatus.ALLOWED) {
            status = DeliveryOrderControlStatus.CANCELLED;
        }
    }

    // 배송 삭제 요청을 기록하고 배송 생성 차단
    public void markDeleted() {
        if (status == DeliveryOrderControlStatus.ALLOWED) {
            throw new DeliveryException(DeliveryErrorCode.DELIVERY_DELETION_NOT_ALLOWED);
        }

        if (status == DeliveryOrderControlStatus.CANCELLED) {
            status = DeliveryOrderControlStatus.DELETED;
        }
    }


}

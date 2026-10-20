package logistics.delivery.domain.entity;

import logistics.delivery.common.exception.DeliveryErrorCode;
import logistics.delivery.common.exception.DeliveryException;
import jakarta.persistence.*;
import logistics.delivery.common.entity.BaseEntity;
import logistics.delivery.domain.enums.DeliveryRouteStatus;
import logistics.delivery.domain.enums.RouteType;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "p_delivery_routes",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"delivery_id", "sequence"})
        }
)
public class DeliveryRoute extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_id", nullable = false)
    private Delivery delivery;

    @Column(nullable = false)
    private Integer sequence;

    @Column
    private UUID managerId;

    @Column(nullable = false)
    private UUID departureHubId;

    // 도착 허브 id가 null : 허브 -> 업체 배송
    @Column
    private UUID destinationHubId;

    // 예상 거리가 null : 허브 -> 업체 배송
    @Column(columnDefinition = "NUMERIC")
    private BigDecimal expectedDistance;

    // 예상 시간이 null : 허브 -> 업체 배송
    @Column
    private Integer expectedDuration;

    @Column(columnDefinition = "NUMERIC")
    private BigDecimal actualDistance;

    @Column
    private Integer actualDuration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DeliveryRouteStatus status = DeliveryRouteStatus.WAITING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RouteType type;

    @Builder
    public DeliveryRoute(Delivery delivery, Integer sequence, UUID departureHubId, UUID destinationHubId,
                         BigDecimal expectedDistance, Integer expectedDuration, RouteType type) {
        this.delivery = delivery;
        this.sequence = sequence;
        this.departureHubId = departureHubId;
        this.destinationHubId = destinationHubId;
        this.expectedDistance = expectedDistance;
        this.expectedDuration = expectedDuration;
        this.type = type;
    }

    /** 출발 전 배송 경로를 취소한다. 이미 취소된 배송 경로는 변경하지 않는다. */
    public void cancel(){
        if(status == DeliveryRouteStatus.CANCELLED) return;
        if(status != DeliveryRouteStatus.WAITING) throw new DeliveryException(DeliveryErrorCode.DELIVERY_ROUTE_CANCELLATION_NOT_ALLOWED);

        status = DeliveryRouteStatus.CANCELLED;
    }

    /** 대기 중인 배송 경로에 담당자를 배정하고 기존 담당자 변경은 거부한다. */
    public void assignManager(UUID managerId){
        if(managerId == null){
            throw new DeliveryException(DeliveryErrorCode.MANAGER_ID_REQUIRED);
        }

        if(status != DeliveryRouteStatus.WAITING){
            throw new DeliveryException(DeliveryErrorCode.DELIVERY_ROUTE_ASSIGNMENT_NOT_ALLOWED);
        }

        if(this.managerId != null){
            if(this.managerId.equals(managerId)) return;

            throw new DeliveryException(DeliveryErrorCode.MANAGER_ALREADY_ASSIGNED);
        }
        this.managerId = managerId;
    }

    /** 배송 경로 상태를 전환한다. 실제 변경 시 true, 동일 상태 재요청 시 false를 반환한다. */
    public boolean updateStatus(DeliveryRouteStatus nextStatus){
        if(nextStatus != DeliveryRouteStatus.IN_TRANSIT
                && nextStatus != DeliveryRouteStatus.ARRIVED){
            throw new DeliveryException(DeliveryErrorCode.UNSUPPORTED_ROUTE_STATUS);
        }

        if(getDeletedAt() != null
            || status == DeliveryRouteStatus.CANCELLED){
            throw new DeliveryException(DeliveryErrorCode.INACTIVE_ROUTE_STATUS_CHANGE);
        }

        if(status == nextStatus) return false;  // 중복 요청

        if (status == DeliveryRouteStatus.WAITING
                && nextStatus == DeliveryRouteStatus.IN_TRANSIT){
            if(managerId == null){
                throw new DeliveryException(DeliveryErrorCode.ROUTE_MANAGER_REQUIRED);
            }
        }
        else if (!(status == DeliveryRouteStatus.IN_TRANSIT
                && nextStatus == DeliveryRouteStatus.ARRIVED)){
            throw new DeliveryException(DeliveryErrorCode.INVALID_ROUTE_STATUS_TRANSITION);
        }
        status = nextStatus;
        return true;
    }


}

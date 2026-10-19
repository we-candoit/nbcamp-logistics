package logistics.delivery.domain.entity;

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

    // 배송 취소 시 배송 경로 취소
    public void cancel(){
        if(status == DeliveryRouteStatus.CANCELLED) return;
        if(status != DeliveryRouteStatus.WAITING) throw new IllegalStateException("출발 전 배송 경로만 취소할 수 있습니다.");

        status = DeliveryRouteStatus.CANCELLED;
    }

    // 배송 경로 담당자 배정
    public void assignManager(UUID managerId){
        if(managerId == null){
            throw new IllegalArgumentException("배정할 담당자 ID가 필요합니다.");
        }

        if(status != DeliveryRouteStatus.WAITING){
            throw new IllegalStateException("대기 중인 배송 경로에만 담당자를 배정할 수 있습니다,");
        }

        if(this.managerId != null){
            if(this.managerId.equals(managerId)) return;

            throw new IllegalStateException("이미 다른 담당자가 배정되어 있습니다.");
        }
        this.managerId = managerId;
    }
}

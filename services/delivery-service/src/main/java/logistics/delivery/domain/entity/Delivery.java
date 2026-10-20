package logistics.delivery.domain.entity;

import logistics.delivery.common.exception.DeliveryErrorCode;
import logistics.delivery.common.exception.DeliveryException;
import jakarta.persistence.*;
import logistics.delivery.common.entity.BaseEntity;
import logistics.delivery.domain.enums.DeliveryRouteStatus;
import logistics.delivery.domain.enums.DeliveryStatus;
import logistics.delivery.domain.enums.RouteType;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "p_deliveries")
public class Delivery extends BaseEntity {

    // 허브 -> 업체 배송담당자
    @Column
    private UUID managerId;

    @Column(nullable = false, unique = true)
    private UUID orderId;

    @Column(nullable = false)
    private UUID departureHubId;

    @Column(nullable = false)
    private UUID destinationHubId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DeliveryStatus status = DeliveryStatus.WAITING;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(nullable = false, length = 50)
    private String recipientName;

    @Column(nullable = false, length = 20)
    private String recipientPhone;

    @Builder
    private Delivery(UUID orderId, UUID departureHubId, UUID destinationHubId,
                    String address, String recipientName, String recipientPhone) {
        this.orderId = orderId;
        this.departureHubId = departureHubId;
        this.destinationHubId = destinationHubId;
        this.address = address;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
    }

    // 기존 배송과 재요청의 내용이 같은지 비교
    public boolean matchesCreation(UUID departureHubId, UUID destinationHubId,
                                   String address, String recipientName, String recipientPhone
    ) {
        return Objects.equals(this.departureHubId, departureHubId)
                && Objects.equals(this.destinationHubId, destinationHubId)
                && Objects.equals(this.address, address)
                && Objects.equals(this.recipientName, recipientName)
                && Objects.equals(this.recipientPhone, recipientPhone);
    }

    /** 출발 전 배송을 취소한다. 이미 취소된 배송은 변경하지 않는다. */
    public void cancel(){
        if(status == DeliveryStatus.CANCELLED) return;
        if(status != DeliveryStatus.WAITING) throw new DeliveryException(DeliveryErrorCode.DELIVERY_CANCELLATION_NOT_ALLOWED);

        status = DeliveryStatus.CANCELLED;
    }

    /** 취소된 배송인지 검증한다. 이미 삭제된 배송은 검증을 생략한다. */
    public void validateDeletable(){
        if(getDeletedAt() != null) return ;
        if(status != DeliveryStatus.CANCELLED){
            throw new DeliveryException(DeliveryErrorCode.DELIVERY_DELETION_NOT_ALLOWED);
        }
    }

    /** 배송 전체가 담당자 배정을 허용하는 상태인지 검증한다. */
    public void validateManagerAssignable(){
        if(status != DeliveryStatus.WAITING
                && status != DeliveryStatus.HUB_IN_TRANSIT
                && status != DeliveryStatus.HUB_ARRIVED){
            throw new DeliveryException(DeliveryErrorCode.DELIVERY_ASSIGNMENT_NOT_ALLOWED);
        }
    }

    /** 담당자를 배정한다. 동일 담당자는 유지하고 다른 담당자로의 변경은 거부한다. */
    public void assignManager(UUID managerId){
        validateManagerAssignable();

        if(managerId == null){
            throw new DeliveryException(DeliveryErrorCode.MANAGER_ID_REQUIRED);
        }

        if(this.managerId != null){
            if(this.managerId.equals(managerId)) return;

            throw new DeliveryException(DeliveryErrorCode.MANAGER_ALREADY_ASSIGNED);
        }

        this.managerId = managerId;
    }

    /** 배송 경로 유형과 실제 변경 상태에 따라 허용된 배송 전체 상태로 전환한다. */
    public void updateStatusByRoute(RouteType routeType, DeliveryRouteStatus routeStatus){
        if(getDeletedAt() != null){
            throw new DeliveryException(DeliveryErrorCode.DELETED_DELIVERY_STATUS_CHANGE);
        }

        // 내부 상태 동기화 인자 오류는 사용자 요청 오류로 오인하지 않는다.
        if (routeType == null || routeStatus == null) {
            throw new IllegalArgumentException("배송 경로 유형과 상태가 필요합니다.");
        }

        // 배송의 다음 상태를 결정
        DeliveryStatus nextStatus = switch (routeType) {
            case HUB_TO_HUB ->
                    switch (routeStatus) {
                        case IN_TRANSIT -> DeliveryStatus.HUB_IN_TRANSIT;
                        case ARRIVED -> DeliveryStatus.HUB_ARRIVED;
                        default -> throw new IllegalArgumentException("지원하지 않는 배송 경로 상태입니다.");
            };

            case HUB_TO_COMPANY ->
                    switch (routeStatus) {
                        case IN_TRANSIT -> DeliveryStatus.COMPANY_IN_TRANSIT;
                        case ARRIVED -> DeliveryStatus.DELIVERED;
                        default -> throw new IllegalArgumentException("지원하지 않는 배송 경로 상태입니다.");
            };
        };

        boolean sameHub = departureHubId.equals(destinationHubId);

        // 현재 상태를 검사한 뒤 조건에 충족 시 상태 변경
        boolean allowed = switch (nextStatus) {
            case HUB_IN_TRANSIT ->
                    !sameHub && status == DeliveryStatus.WAITING;

            case HUB_ARRIVED ->
                    !sameHub && status == DeliveryStatus.HUB_IN_TRANSIT;

            case COMPANY_IN_TRANSIT ->
                    sameHub
                            ? status == DeliveryStatus.WAITING
                            : status == DeliveryStatus.HUB_ARRIVED;

            case DELIVERED ->
                    status == DeliveryStatus.COMPANY_IN_TRANSIT;

            default -> false;
        };

        if (!allowed) {
            throw new DeliveryException(DeliveryErrorCode.INVALID_DELIVERY_STATUS_TRANSITION);
        }

        status = nextStatus;
    }
}

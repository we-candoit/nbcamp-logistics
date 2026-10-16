package logistics.delivery.domain.entity;

import jakarta.persistence.*;
import logistics.delivery.common.entity.BaseEntity;
import logistics.delivery.domain.enums.DeliveryStatus;
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

    // 배송 취소
    public void cancel(){
        if(status == DeliveryStatus.CANCELLED) return;
        if(status != DeliveryStatus.WAITING) throw new IllegalStateException("출발 전 배송만 취소할 수 있습니다.");

        status = DeliveryStatus.CANCELLED;
    }

    // 배송 삭제를 위해 취소된 배송인지 검증
    public void validateDeletable(){
        if(getDeletedAt() != null) return ;
        if(status != DeliveryStatus.CANCELLED){
            throw new IllegalStateException("취소된 배송만 삭제할 수 있습니다.");
        }
    }
}

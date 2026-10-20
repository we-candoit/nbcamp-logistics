package logistics.delivery.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import logistics.delivery.domain.entity.Delivery;
import logistics.delivery.domain.enums.DeliveryStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "배송 상세 조회 응답")
public record DeliveryDetailResponse(
        @Schema(description = "배송 ID")
        UUID deliveryId,

        @Schema(description = "주문 ID")
        UUID orderId,

        @Schema(description = """
                도착 허브에서 수령 업체까지 배송하는 최종 구간 배송 담당자 ID.
                허브 간 이동 구간의 배송 담당자와 다를 수 있음,
                미배정 상태 시 null 반환
                """)
        UUID managerId,

        @Schema(description = "출발 허브 ID")
        UUID departureHubId,

        @Schema(description = "도착 허브 ID")
        UUID destinationHubId,

        @Schema(description = "배송 상태")
        DeliveryStatus status,

        @Schema(description = "배송지 주소")
        String address,

        @Schema(description = "수령인 이름")
        String recipientName,

        @Schema(description = "수령인 연락처")
        String recipientPhone,

        @Schema(description = "생성 시간")
        LocalDateTime createdAt,

        @Schema(description = "최종 수정 시간")
        LocalDateTime updatedAt
) {
    public static DeliveryDetailResponse from(Delivery delivery){
        return new DeliveryDetailResponse(
                delivery.getId(),
                delivery.getOrderId(),
                delivery.getManagerId(),
                delivery.getDepartureHubId(),
                delivery.getDestinationHubId(),
                delivery.getStatus(),
                delivery.getAddress(),
                delivery.getRecipientName(),
                delivery.getRecipientPhone(),
                delivery.getCreatedAt(),
                delivery.getUpdatedAt()
        );
    }
}

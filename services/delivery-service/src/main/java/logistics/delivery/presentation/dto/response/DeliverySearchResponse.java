package logistics.delivery.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import logistics.delivery.domain.entity.Delivery;
import logistics.delivery.domain.enums.DeliveryStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "배송 목록 조회 응답")
public record DeliverySearchResponse(

        @Schema(description = "배송 ID")
        UUID deliveryId,

        @Schema(description = "주문 ID")
        UUID orderId,

        @Schema(description = "도착 허브에서 업체로 배송하는 담당자ID, 미배정 시 null")
        UUID managerId,

        @Schema(description = "출발 허브ID")
        UUID departureHubId,

        @Schema(description = "도착 허브ID")
        UUID destinationHubId,

        @Schema(description = "배송 상태")
        DeliveryStatus status,

        @Schema(description = "배송 생성일")
        LocalDateTime createdAt,

        @Schema(description = "배송 수정일")
        LocalDateTime updatedAt
) {
    public static DeliverySearchResponse from(Delivery delivery){
        return new DeliverySearchResponse(
                delivery.getId(),
                delivery.getOrderId(),
                delivery.getManagerId(),
                delivery.getDepartureHubId(),
                delivery.getDestinationHubId(),
                delivery.getStatus(),
                delivery.getCreatedAt(),
                delivery.getUpdatedAt()
        );
    }
}

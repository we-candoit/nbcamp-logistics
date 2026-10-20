package logistics.delivery.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import logistics.delivery.domain.enums.DeliveryStatus;

import java.util.UUID;

@Schema(description = "배송 목록 검색 조건")
public record DeliverySearchRequest(
        @Schema(description = "배송 상태")
        DeliveryStatus status,

        @Schema(description = "출발 허브 ID")
        UUID departureHubId,

        @Schema(description = "도착 허브 ID")
        UUID destinationHubId,

        @Schema(description = "배송 담당자 또는 배송 경로 담당자 ID")
        UUID managerId,

        @Schema(description = "주문 ID")
        UUID orderId
) {
}

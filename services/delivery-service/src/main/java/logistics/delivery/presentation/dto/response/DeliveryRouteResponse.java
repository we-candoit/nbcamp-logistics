package logistics.delivery.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import logistics.delivery.domain.entity.DeliveryRoute;
import logistics.delivery.domain.enums.DeliveryRouteStatus;
import logistics.delivery.domain.enums.RouteType;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "배송 경로 조회 응답")
public record DeliveryRouteResponse(

        @Schema(description = "배송 경로 ID")
        UUID deliveryRouteId,

        @Schema(description = "배송 경로 순서")
        Integer sequence,

        @Schema(description = "해당 구간의 배송 담당자ID, 미배정 시 null")
        UUID managerId,

        @Schema(description = "출발 허브 ID")
        UUID departureHubId,

        @Schema(description = "도착 허브 ID, 허브에서 업체로 배송하는 구간은 null")
        UUID destinationHubId,

        @Schema(description = "예상 거리(km), 허브에서 업체로 배송하는 구간은 null")
        BigDecimal expectedDistance,

        @Schema(description = "예상 소요 시간(minutes), 허브에서 업체 배송하는 구간은 null")
        Integer expectedDuration,

        @Schema(description = "실제 이동 거리(km), 미기록 시 null")
        BigDecimal actualDistance,

        @Schema(description = "실제 소요 시간(minutes), 미기록 시 null")
        Integer actualDuration,

        @Schema(description = "배송 경로 상태", example = "WAITING")
        DeliveryRouteStatus status,

        @Schema(description = "배송 경로 유횽", example = "HUB_TO_HUB")
        RouteType type
) {
    public static DeliveryRouteResponse from(DeliveryRoute route){
        return new DeliveryRouteResponse(
                route.getId(),
                route.getSequence(),
                route.getManagerId(),
                route.getDepartureHubId(),
                route.getDestinationHubId(),
                route.getExpectedDistance(),
                route.getExpectedDuration(),
                route.getActualDistance(),
                route.getActualDuration(),
                route.getStatus(),
                route.getType()
        );
    }
}

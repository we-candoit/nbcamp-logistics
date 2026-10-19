package logistics.delivery.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import logistics.delivery.domain.entity.Delivery;
import logistics.delivery.domain.entity.DeliveryRoute;
import logistics.delivery.domain.enums.DeliveryRouteStatus;
import logistics.delivery.domain.enums.DeliveryStatus;

import java.util.UUID;

@Schema(description = "배송 경로 상태 변경 응답")
public record DeliveryStatusUpdateResponse(
        @Schema(description = "배송 ID")
        UUID deliveryId,

        @Schema(description = "배송 경로 ID")
        UUID deliveryRouteId,

        @Schema(description = "배송 경로 상태")
        DeliveryRouteStatus routeStatus,

        @Schema(description = "배송 전체 상태")
        DeliveryStatus deliveryStatus
) {
    public static DeliveryStatusUpdateResponse from(Delivery delivery, DeliveryRoute route){
        return new DeliveryStatusUpdateResponse(
                delivery.getId(),
                route.getId(),
                route.getStatus(),
                delivery.getStatus()
        );
    }
}

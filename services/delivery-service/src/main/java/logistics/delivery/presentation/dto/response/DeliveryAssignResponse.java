package logistics.delivery.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import logistics.delivery.domain.entity.DeliveryRoute;

import java.util.UUID;

@Schema(description = "배송 경로 담당자 배정 응답")
public record DeliveryAssignResponse(

        @Schema(description = "배송ID")
        UUID deliveryId,

        @Schema(description = "배송 경로ID")
        UUID deliveryRouteId,

        @Schema(description = "배정된 배송 담당자의 사용자ID")
        UUID managerId
) {
    public static DeliveryAssignResponse from(DeliveryRoute route){
        return new DeliveryAssignResponse(
                route.getDelivery().getId(),
                route.getId(),
                route.getManagerId()
        );
    }
}

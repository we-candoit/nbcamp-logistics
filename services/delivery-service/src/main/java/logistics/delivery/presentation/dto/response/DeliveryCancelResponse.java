package logistics.delivery.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import logistics.delivery.domain.entity.Delivery;
import logistics.delivery.domain.enums.DeliveryStatus;

import java.util.UUID;
@Schema(description = "배송 취소 응답")
public record DeliveryCancelResponse(

        @Schema(description = "취소 요청 배송 ID")
        UUID deliveryId,

        @Schema(description = "배송 상태", example = "CANCELLED")
        DeliveryStatus status
) {
    public static DeliveryCancelResponse from(Delivery delivery){
        return new DeliveryCancelResponse(
                delivery.getId(),
                delivery.getStatus()
        );
    }
}

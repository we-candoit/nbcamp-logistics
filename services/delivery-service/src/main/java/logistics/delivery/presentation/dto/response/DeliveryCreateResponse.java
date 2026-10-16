package logistics.delivery.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import logistics.delivery.domain.entity.Delivery;
import logistics.delivery.domain.enums.DeliveryStatus;

import java.util.UUID;

@Schema(description = "배송 생성 응답")
public record DeliveryCreateResponse(
        @Schema(description = "생성된 배송 ID")
        UUID deliveryId,

        @Schema(description = "연결된 주문 ID")
        UUID orderId,

        @Schema(description = "배송 상태", example = "WAITING")
        DeliveryStatus status
) {
    public static DeliveryCreateResponse from(Delivery delivery){
        return new DeliveryCreateResponse(
                delivery.getId(),
                delivery.getOrderId(),
                delivery.getStatus()
        );
    }
}

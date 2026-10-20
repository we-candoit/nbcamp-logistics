package logistics.delivery.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import logistics.delivery.domain.enums.DeliveryRouteStatus;

@Schema(description = "배송 경로 상태 변경 요청")
public record DeliveryStatusUpdateRequest(
        @Schema(
                description = "변경할 배송 경로 상태",
                allowableValues = {"IN_TRANSIT", "ARRIVED"},    // WAITING으로 되돌리거나 CANCELLED로 변경하는 요청은 불허용
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull
        DeliveryRouteStatus status
) {
}

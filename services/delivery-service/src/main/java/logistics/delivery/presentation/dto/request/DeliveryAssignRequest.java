package logistics.delivery.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "배송 경로 담당자 배정 요청")
public record DeliveryAssignRequest(
        @Schema(
                description = "배정할 배송 담당자의 사용자 ID",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull
        UUID managerId
) {
}

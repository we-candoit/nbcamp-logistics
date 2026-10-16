package logistics.delivery.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import logistics.delivery.domain.entity.Delivery;

import java.util.UUID;

public record DeliveryCreateRequest(
        @Schema(description = "주문 ID")
        @NotNull
        UUID orderId,

        @Schema(description = "주문에서 확정한 출발 허브 ID")
        @NotNull
        UUID departureHubId,

        @Schema(description = "주문에서 확정한 도착 허브 ID")
        @NotNull
        UUID destinationHubId,

        @Schema(description = "배송지 주소")
        @NotBlank
        @Size(max = 255)
        String address,

        @Schema(description = "수령인 이름")
        @NotBlank
        @Size(max = 50)
        String recipientName,

        @Schema(description = "수령인 연락처")
        @NotBlank
        @Size(max = 20)
        String recipientPhone
) {
    public Delivery toEntity(){
        return Delivery.builder()
                .orderId(orderId)
                .departureHubId(departureHubId)
                .destinationHubId(destinationHubId)
                .address(address)
                .recipientName(recipientName)
                .recipientPhone(recipientPhone)
                .build();
    }
}

package logistics.delivery.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserDetailResponse(
        UUID userId,
        Boolean isDeliveryManager,
        String affiliationType,
        String affiliationStatus,
        UUID hubId
) {
}

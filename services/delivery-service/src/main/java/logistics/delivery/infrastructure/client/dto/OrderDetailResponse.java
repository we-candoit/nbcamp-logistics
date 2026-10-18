package logistics.delivery.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderDetailResponse(
        UUID orderId,
        UUID orderUserId
) {
}
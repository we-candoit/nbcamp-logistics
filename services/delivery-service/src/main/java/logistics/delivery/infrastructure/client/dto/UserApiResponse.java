package logistics.delivery.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserApiResponse<T>(
        Boolean success,
        String message,
        T data
) {
}

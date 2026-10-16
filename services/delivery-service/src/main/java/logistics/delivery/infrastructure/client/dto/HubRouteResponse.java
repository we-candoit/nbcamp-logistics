package logistics.delivery.infrastructure.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record HubRouteResponse(
        UUID destinationHubId,
        String destinationHubName,
        BigDecimal distance,
        Integer duration,
        UUID id,    // 허브경로 id 인듯!
        UUID originHubId,
        String originHubName
) {
}

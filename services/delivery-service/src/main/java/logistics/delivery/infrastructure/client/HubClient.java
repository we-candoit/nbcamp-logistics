package logistics.delivery.infrastructure.client;

import logistics.delivery.infrastructure.client.dto.HubRouteResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "hub-service")
public interface HubClient {

    @GetMapping("/internal/hubs/routes/hubs")
    HubRouteResponse getRoute(
            @RequestParam("originHubId") UUID departureHubId,
            @RequestParam("destinationHubId") UUID destinationHubId
    );
}

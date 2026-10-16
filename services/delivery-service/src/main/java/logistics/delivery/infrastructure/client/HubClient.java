package logistics.delivery.infrastructure.client;

import logistics.delivery.infrastructure.client.dto.HubRouteResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "hub-service")
public interface HubClient {

    @GetMapping("/api/hubs/routes")
    HubRouteResponse getRoute(
            @RequestParam("departureHubId") UUID departureHubId,
            @RequestParam("destinationHubId") UUID destinationHubId
    );
}

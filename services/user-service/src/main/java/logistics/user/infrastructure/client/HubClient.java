package logistics.user.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "${HUB_SERVICE_NAME}")
public interface HubClient {

    @GetMapping("/internal/hubs")
    HubResponse getHubByName(@RequestParam(name = "name") String hubName);
}
package logistics.order.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;


@FeignClient(name = "${HUB_SERVICE_NAME}")
public interface HubServiceClient {

    @PostMapping("/internal/hubs/{hubId}/items/{itemId}/deductions")
    String decreaseStock(@PathVariable("hubId") UUID hubId,
                         @PathVariable("itemId") UUID itemId,
                         @RequestParam("quantity") Long quantity);

    @PostMapping("/internal/hubs/{hubId}/items/{itemId}/restorations")
    String restoreStock(@PathVariable("hubId") UUID hubId,
                         @PathVariable("itemId") UUID itemId,
                         @RequestParam("quantity") Long quantity);

}

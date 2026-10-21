package logistics.order.infrastructure.client;

import logistics.order.infrastructure.client.dto.StockResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;


@FeignClient(name = "${HUB_SERVICE_NAME}")
public interface HubServiceClient {

    @PostMapping("/internal/hubs/{hubId}/items/{itemId}/deductions")
    StockResponse decreaseStock(@PathVariable("hubId") UUID hubId,
                                @PathVariable("itemId") UUID itemId,
                                @RequestParam("quantity") Long quantity,
                                @RequestParam("orderId") UUID orderId);

    @PostMapping("/internal/hubs/{hubId}/items/{itemId}/restorations")
    StockResponse restoreStock(@PathVariable("hubId") UUID hubId,
                               @PathVariable("itemId") UUID itemId,
                               @RequestParam("quantity") Long quantity,
                               @RequestParam("orderId") UUID orderId);

}

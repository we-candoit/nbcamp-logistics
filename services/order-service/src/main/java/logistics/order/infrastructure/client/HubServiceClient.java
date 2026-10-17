package logistics.order.infrastructure.client;

import logistics.order.infrastructure.dto.DecreaseStockRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@FeignClient(name = "${HUB_SERVICE_NAME}")
public interface HubServiceClient {

    @PostMapping("/internal/hubs/items/deductions")
    String decreaseStock(@RequestBody DecreaseStockRequest request);

}

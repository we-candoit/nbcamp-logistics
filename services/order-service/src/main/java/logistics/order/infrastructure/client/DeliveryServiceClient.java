package logistics.order.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "${DELIVERY_SERVICE_NAME}")
public interface DeliveryServiceClient {

    //아직 API명세가 없음
    @PostMapping("/internal/deliveries")
    String createDelivery();

}

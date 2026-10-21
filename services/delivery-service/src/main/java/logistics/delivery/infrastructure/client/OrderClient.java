package logistics.delivery.infrastructure.client;

import logistics.delivery.infrastructure.client.dto.OrderDetailResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

@FeignClient(name = "order-service")
public interface OrderClient {

    @GetMapping("/api/orders/{id}")
    OrderDetailResponse getOrderDetail(
            @PathVariable("id") UUID orderId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-Role-Name") String role
    );
}

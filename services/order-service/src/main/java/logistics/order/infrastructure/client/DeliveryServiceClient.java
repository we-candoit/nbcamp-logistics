package logistics.order.infrastructure.client;

import logistics.order.infrastructure.client.dto.CancelDeliveryResponse;
import logistics.order.infrastructure.client.dto.CreateDeliveryRequest;
import logistics.order.infrastructure.client.dto.CreateDeliveryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.UUID;

@FeignClient(name = "${DELIVERY_SERVICE_NAME}")
public interface DeliveryServiceClient {

    @PostMapping("/internal/deliveries")
    CreateDeliveryResponse createDelivery(CreateDeliveryRequest createDeliveryRequest);

    @PostMapping("/internal/deliveries/{id}/cancel")
    CancelDeliveryResponse cancelDelivery(@PathVariable("id") UUID deliveryId);

    //성공 시 204 No Content / 이미 삭제된 배송이면 204 (멱등)
    @DeleteMapping("/internal/deliveries/{id}")
    void deleteDelivery(@PathVariable("id") UUID deliveryId);

}

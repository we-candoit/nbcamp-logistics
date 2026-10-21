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

    //주문ID로 배송취소 / 배송이 없으면 이후 배송생성을 차단 (보상처리에서 사용)
    @PostMapping("/internal/deliveries/orders/{orderId}/cancel")
    void cancelDeliveryByOrderId(@PathVariable("orderId") UUID orderId);

    //주문ID로 배송삭제 / 취소된 배송 또는 생성 차단된 주문만 가능 (보상처리에서 사용)
    @DeleteMapping("/internal/deliveries/orders/{orderId}")
    void deleteDeliveryByOrderId(@PathVariable("orderId") UUID orderId);

}

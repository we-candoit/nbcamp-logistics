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

    //주문ID로 배송취소 / 이미 취소된 배송이면 기존 결과 반환, 배송이 없으면 이후 배송생성을 차단
    @PostMapping("/internal/deliveries/orders/{orderId}/cancel")
    CancelDeliveryResponse cancelDeliveryByOrderId(@PathVariable("orderId") UUID orderId);

    //주문ID로 배송삭제 / 성공 및 이미 삭제된 경우 204, 취소된 배송 또는 생성 차단된 주문만 가능
    @DeleteMapping("/internal/deliveries/orders/{orderId}")
    void deleteDeliveryByOrderId(@PathVariable("orderId") UUID orderId);

}

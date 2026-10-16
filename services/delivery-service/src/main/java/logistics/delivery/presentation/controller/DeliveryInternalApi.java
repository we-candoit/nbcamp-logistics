package logistics.delivery.presentation.controller;

import logistics.delivery.application.DeliveryCreateResult;
import logistics.delivery.application.DeliveryService;
import logistics.delivery.presentation.dto.request.DeliveryCreateRequest;
import logistics.delivery.presentation.dto.response.DeliveryCancelResponse;
import logistics.delivery.presentation.dto.response.DeliveryCreateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/deliveries")
public class DeliveryInternalApi implements DeliveryApiSpec{

    private final DeliveryService deliveryService;

    @Override
    @PostMapping
    public ResponseEntity<DeliveryCreateResponse> createDelivery(DeliveryCreateRequest request) {
        DeliveryCreateResult result = deliveryService.createDelivery(request);

        // 신규 배송인 경우 201, 기존 배송인 경우 200
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.response());
    }

    @Override
    @PostMapping("/{id}/cancel")
    public ResponseEntity<DeliveryCancelResponse> cancelDelivery(UUID deliveryId) {
        DeliveryCancelResponse respone = deliveryService.cancelDelivery(deliveryId);
        return ResponseEntity.ok(respone);
    }

    @Override
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDelivery(UUID deliveryId) {
        deliveryService.deleteDelivery(deliveryId);
        return ResponseEntity.noContent().build();
    }
}

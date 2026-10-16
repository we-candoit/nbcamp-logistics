package logistics.delivery.presentation.controller;

import logistics.delivery.application.DeliveryService;
import logistics.delivery.presentation.dto.request.DeliveryCreateRequest;
import logistics.delivery.presentation.dto.response.DeliveryCreateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/deliveries")
public class DeliveryInternalApi implements DeliveryApiSpec{

    private final DeliveryService deliveryService;

    @Override
    @PostMapping
    public ResponseEntity<DeliveryCreateResponse> createDelivery(DeliveryCreateRequest request) {
        DeliveryCreateResponse response = deliveryService.createDelivery(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

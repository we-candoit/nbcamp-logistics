package logistics.delivery.presentation.controller;

import jakarta.servlet.http.HttpServletRequest;
import logistics.delivery.application.DeliveryService;
import logistics.delivery.presentation.dto.response.DeliveryDetailResponse;
import logistics.delivery.presentation.interceptor.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/deliveries")
public class DeliveryApi implements DeliveryApiSpec{


    private final DeliveryService deliveryService;

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<DeliveryDetailResponse> getDeliveryDetail(UUID deliveryId, HttpServletRequest request) {
        UUID userId = AuthInterceptor.getUserId(request);
        String username = (String) request.getAttribute(AuthInterceptor.USER_HEADER);
        String role = (String) request.getAttribute(AuthInterceptor.ROLE_HEADER);

        DeliveryDetailResponse response = deliveryService.getDeliveryDetail(deliveryId, userId, username, role);
        return ResponseEntity.ok(response);
    }
}

package logistics.delivery.presentation.controller;

import jakarta.servlet.http.HttpServletRequest;
import logistics.delivery.application.DeliveryService;
import logistics.delivery.presentation.dto.request.DeliveryAssignRequest;
import logistics.delivery.presentation.dto.request.DeliverySearchRequest;
import logistics.delivery.presentation.dto.request.DeliveryStatusUpdateRequest;
import logistics.delivery.presentation.dto.response.*;
import logistics.delivery.presentation.interceptor.AuthInterceptor;
import logistics.delivery.presentation.util.DeliveryPageableUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/deliveries")
public class DeliveryApi implements DeliveryApiSpec{


    private final DeliveryService deliveryService;

    @Override
    @GetMapping
    public ResponseEntity<Page<DeliverySearchResponse>> searchDeliveries(
            DeliverySearchRequest searchRequest,
            int page, int size, String sort,
            HttpServletRequest request
    ) {
        UUID userId = AuthInterceptor.getUserId(request);
        String role = (String) request.getAttribute(AuthInterceptor.ROLE_HEADER);

        Pageable pageable = DeliveryPageableUtils.create(page, size, sort);

        Page<DeliverySearchResponse> response =
                deliveryService.searchDeliveries(searchRequest, pageable, userId, role);

        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<DeliveryDetailResponse> getDeliveryDetail(UUID deliveryId, HttpServletRequest request) {
        UUID userId = AuthInterceptor.getUserId(request);
        String username = (String) request.getAttribute(AuthInterceptor.USER_HEADER);
        String role = (String) request.getAttribute(AuthInterceptor.ROLE_HEADER);

        DeliveryDetailResponse response = deliveryService.getDeliveryDetail(deliveryId, userId, username, role);
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/{id}/routes")
    public ResponseEntity<List<DeliveryRouteResponse>> getDeliveryRoutes(UUID deliveryId, HttpServletRequest request) {
        UUID userId = AuthInterceptor.getUserId(request);
        String username = (String) request.getAttribute(AuthInterceptor.USER_HEADER);
        String role = (String) request.getAttribute(AuthInterceptor.ROLE_HEADER);

        List<DeliveryRouteResponse> responses = deliveryService.getDeliveryRoutes(deliveryId, userId, username, role);
        return ResponseEntity.ok(responses);
    }

    @Override
    @PutMapping("/{deliveryId}/routes/{routeId}/assignee")
    public ResponseEntity<DeliveryAssignResponse> assignDeliveryManager(
            UUID deliveryId, UUID routeId, DeliveryAssignRequest assignRequest, HttpServletRequest request) {

        UUID requesterId = AuthInterceptor.getUserId(request);

        String role = (String) request.getAttribute(AuthInterceptor.ROLE_HEADER);

        DeliveryAssignResponse response = deliveryService.assignDeliveryManager(
                deliveryId, routeId, assignRequest, requesterId, role);

        return ResponseEntity.ok(response);
    }

    @Override
    @PatchMapping("/{deliveryId}/routes/{routeId}/status")
    public ResponseEntity<DeliveryStatusUpdateResponse> updateDeliveryStatus(
            UUID deliveryId, UUID routeId, DeliveryStatusUpdateRequest updateRequest, HttpServletRequest request) {

        UUID requesterId = AuthInterceptor.getUserId(request);

        String role = (String) request.getAttribute(
                AuthInterceptor.ROLE_HEADER
        );

        DeliveryStatusUpdateResponse response =
                deliveryService.updateDeliveryStatus(
                        deliveryId,
                        routeId,
                        updateRequest,
                        requesterId,
                        role
                );

        return ResponseEntity.ok(response);
    }
}

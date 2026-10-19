package logistics.delivery.application;

import logistics.delivery.presentation.dto.request.DeliveryAssignRequest;
import logistics.delivery.presentation.dto.request.DeliveryCreateRequest;
import logistics.delivery.presentation.dto.request.DeliverySearchRequest;
import logistics.delivery.presentation.dto.request.DeliveryStatusUpdateRequest;
import logistics.delivery.presentation.dto.response.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface DeliveryService {

    DeliveryCreateResult createDelivery(DeliveryCreateRequest request);

    DeliveryCancelResponse cancelDelivery(UUID deliveryId);

    void deleteDelivery(UUID deliveryId);

    DeliveryDetailResponse getDeliveryDetail(
            UUID deliveryId,
            UUID userId,
            String role
    );

    List<DeliveryRouteResponse> getDeliveryRoutes(
            UUID deliveryId,
            UUID userId,
            String role
    );

    Page<DeliverySearchResponse> searchDeliveries(
            DeliverySearchRequest request,
            Pageable pageable,
            UUID userId,
            String role
    );

    DeliveryAssignResponse assignDeliveryManager(
            UUID deliveryId,
            UUID routeId,
            DeliveryAssignRequest request,
            UUID requesterId,
            String role
    );

    DeliveryStatusUpdateResponse updateDeliveryStatus(
            UUID deliveryId,
            UUID routeId,
            DeliveryStatusUpdateRequest request,
            UUID requesterId,
            String role
    );
}

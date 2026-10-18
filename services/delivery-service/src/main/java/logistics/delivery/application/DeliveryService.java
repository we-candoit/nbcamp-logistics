package logistics.delivery.application;

import logistics.delivery.presentation.dto.request.DeliveryCreateRequest;
import logistics.delivery.presentation.dto.response.DeliveryCancelResponse;
import logistics.delivery.presentation.dto.response.DeliveryDetailResponse;
import logistics.delivery.presentation.dto.response.DeliveryRouteResponse;

import java.util.List;
import java.util.UUID;

public interface DeliveryService {
    DeliveryCreateResult createDelivery(DeliveryCreateRequest request);
    DeliveryCancelResponse cancelDelivery(UUID deliveryId);
    void deleteDelivery(UUID deliveryId);
    DeliveryDetailResponse getDeliveryDetail(UUID deliveryId, UUID userId, String username, String role);
    List<DeliveryRouteResponse> getDeliveryRoutes(UUID deliveryId, UUID userId, String username, String role);
}

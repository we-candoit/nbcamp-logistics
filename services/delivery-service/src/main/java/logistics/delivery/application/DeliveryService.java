package logistics.delivery.application;

import logistics.delivery.presentation.dto.request.DeliveryCreateRequest;
import logistics.delivery.presentation.dto.response.DeliveryCancelResponse;

import java.util.UUID;

public interface DeliveryService {
    DeliveryCreateResult createDelivery(DeliveryCreateRequest request);
    DeliveryCancelResponse cancelDelivery(UUID deliveryId);
    void deleteDelivery(UUID deliveryId);
}

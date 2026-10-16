package logistics.delivery.application;

import logistics.delivery.presentation.dto.request.DeliveryCreateRequest;
import logistics.delivery.presentation.dto.response.DeliveryCreateResponse;

public interface DeliveryService {
    DeliveryCreateResult createDelivery(DeliveryCreateRequest request);
}

package logistics.hub.application.service;

import logistics.hub.presentation.dto.response.InventoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryMovementService {

    private final InventoryWriter inventoryWriter;

    public InventoryResponse deduct(
            UUID hubId, UUID itemId, UUID orderId, Long quantity, UUID userId
    ) {
        validateCallerAndOrder(userId, orderId);
        return inventoryWriter.deduct(hubId, itemId, orderId, toInventoryQuantity(quantity), userId);
    }

    public InventoryResponse restore(
            UUID hubId, UUID itemId, UUID orderId, Long quantity, UUID userId
    ) {
        validateCallerAndOrder(userId, orderId);
        return inventoryWriter.restore(hubId, itemId, orderId, toInventoryQuantity(quantity), userId);
    }

    private void validateCallerAndOrder(UUID userId, UUID orderId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "요청 사용자 정보를 확인할 수 없습니다.");
        }
        if (orderId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "주문 ID는 필수입니다.");
        }
    }

    private Integer toInventoryQuantity(Long quantity) {
        if (quantity == null || quantity <= 0 || quantity > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "수량은 1 이상, Integer 최대값 이하여야 합니다.");
        }
        return quantity.intValue();
    }
}

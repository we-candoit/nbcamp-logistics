package logistics.hub.application.service;

import logistics.hub.domain.entity.Inventory;
import logistics.hub.infrastructure.repository.HubRepository;
import logistics.hub.infrastructure.repository.InventoryRepository;
import logistics.hub.presentation.dto.response.InventoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryService {

    private final HubRepository hubRepository;
    private final InventoryRepository inventoryRepository;

    // 상품들을 모두 조회
    public List<InventoryResponse> getInventories(UUID hubId) {
        validateHub(hubId);

        List<Inventory> inventories = inventoryRepository.findAllByHubIdAndDeletedAtIsNull(hubId);

        return inventories.stream()
                .map(InventoryResponse::new)
                .toList();
    }

    // 해당 상품의 재고가 존재 하는지
    public InventoryResponse getInventory(UUID hubId, UUID productId) {
        validateHub(hubId);

        Inventory inventory = inventoryRepository.findByHubIdAndProductIdAndDeletedAtIsNull(hubId,productId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "해당 상품의 재고를 찾을 수 없습니다."
                        )
                );

        return new InventoryResponse(inventory);
    }

    // 해당 허브가 존재 하는지 / 재고 여부와는 상관 없음
    private void validateHub(UUID hubId) {
        hubRepository.findByIdAndDeletedAtIsNull(hubId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "해당 허브를 찾을 수 없습니다."
                        )
                );
    }
}
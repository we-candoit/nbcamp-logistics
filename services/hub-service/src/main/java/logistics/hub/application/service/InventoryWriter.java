package logistics.hub.application.service;

import logistics.hub.domain.entity.Hub;
import logistics.hub.domain.entity.Inventory;
import logistics.hub.domain.entity.InventoryRestock;
import logistics.hub.infrastructure.repository.HubRepository;
import logistics.hub.infrastructure.repository.InventoryRepository;
import logistics.hub.infrastructure.repository.InventoryRestockRepository;
import logistics.hub.presentation.dto.response.InventoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryWriter {

    private final HubRepository hubRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryRestockRepository restockRepository;

    @Transactional
    public InventoryResponse create(
            UUID hubId,
            UUID productId,
            Integer quantity,
            UUID userId
    ) {
        Hub hub = hubRepository
                .findByIdAndDeletedAtIsNull(hubId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "해당 허브를 찾을 수 없습니다."
                        )
                );

        if (inventoryRepository.existsByHubIdAndProductId(
                hubId,
                productId
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이미 등록된 재고입니다."
            );
        }

        Inventory inventory = new Inventory(
                hub,
                productId,
                quantity,
                userId
        );

        inventoryRepository.save(inventory);

        return new InventoryResponse(inventory);
    }

    @Transactional
    public InventoryResponse restock(
            UUID hubId,
            UUID productId,
            UUID companyId,
            UUID restockRequestId,
            Integer quantity,
            UUID userId
    ) {
        if (restockRequestId == null || companyId == null || userId == null || quantity == null || quantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "입고 요청 정보를 확인해주세요.");
        }

        hubRepository.findByIdAndDeletedAtIsNull(hubId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "해당 허브를 찾을 수 없습니다."
                        )
                );

        Inventory inventory = inventoryRepository
                .findLockedByHubIdAndProductIdAndDeletedAtIsNull(hubId, productId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "최초 재고 등록이 필요합니다."
                        )
                );

        // 같은 재고 행을 먼저 잠근 뒤 처리 기록을 검사한다.
        // 동시에 같은 요청이 와도 앞선 트랜잭션의 완료 후 기록을 확인한다.
        Optional<InventoryRestock> previous = restockRepository
                .findByInventoryIdAndRequestId(inventory.getId(), restockRequestId);

        if (previous.isPresent()) {
            InventoryRestock completed = previous.get();
            if (!completed.matches(companyId, quantity, userId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "동일한 입고 요청 ID를 다른 요청 내용으로 사용할 수 없습니다.");
            }
            return new InventoryResponse(inventory, completed.getResultQuantity());
        }

        try {
            inventory.restock(quantity, userId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    e.getMessage(),
                    e
            );
        }

        // 수량 변경과 처리 기록 저장은 같은 트랜잭션에서 함께 커밋/롤백된다.
        restockRepository.save(new InventoryRestock(inventory, restockRequestId, companyId, quantity, userId));

        return new InventoryResponse(inventory);
    }
}
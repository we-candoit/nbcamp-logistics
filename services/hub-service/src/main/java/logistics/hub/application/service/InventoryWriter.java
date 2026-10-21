package logistics.hub.application.service;

import logistics.hub.domain.entity.Hub;
import logistics.hub.domain.entity.Inventory;
import logistics.hub.domain.entity.InventoryMovement;
import logistics.hub.domain.entity.InventoryRestock;
import logistics.hub.domain.enums.InventoryMovementType;
import logistics.hub.infrastructure.repository.HubRepository;
import logistics.hub.infrastructure.repository.InventoryMovementRepository;
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
    private final InventoryMovementRepository movementRepository;

    @Transactional
    public InventoryResponse create(
            UUID hubId,
            UUID productId,
            Integer quantity,
            UUID userId
    ) {
        Hub hub = hubRepository.findByIdAndDeletedAtIsNull(hubId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "해당 허브를 찾을 수 없습니다."));

        if (inventoryRepository.existsByHubIdAndProductId(hubId, productId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 등록된 재고입니다.");
        }

        Inventory inventory = new Inventory(hub, productId, quantity, userId);
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
        if (restockRequestId == null || companyId == null || userId == null
                || quantity == null || quantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "입고 요청 정보를 확인해주세요.");
        }

        hubRepository.findByIdAndDeletedAtIsNull(hubId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "해당 허브를 찾을 수 없습니다."));

        Inventory inventory = findLockedInventory(hubId, productId);
        Optional<InventoryRestock> previous = restockRepository.findByInventoryIdAndRequestId(inventory.getId(), restockRequestId);

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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }

        restockRepository.save(new InventoryRestock(inventory, restockRequestId, companyId, quantity, userId));
        return new InventoryResponse(inventory);
    }

    @Transactional
    public InventoryResponse deduct(
            UUID hubId,
            UUID productId,
            UUID orderId,
            Integer quantity,
            UUID userId
    ) {
        Inventory inventory = findLockedInventory(hubId, productId);
        Optional<InventoryMovement> previous = movementRepository.findByInventoryIdAndOrderIdAndType(inventory.getId(), orderId, InventoryMovementType.DEDUCTION);

        if (previous.isPresent()) {
            return existingMovementResponse(inventory, previous.get(), quantity);
        }

        try {
            inventory.deduct(quantity, userId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }

        movementRepository.save(new InventoryMovement(inventory, orderId, InventoryMovementType.DEDUCTION, quantity, userId));
        return new InventoryResponse(inventory);
    }

    @Transactional
    public InventoryResponse restore(
            UUID hubId,
            UUID productId,
            UUID orderId,
            Integer quantity,
            UUID userId
    ) {
        Inventory inventory = findLockedInventory(hubId, productId);
        Optional<InventoryMovement> previous = movementRepository.findByInventoryIdAndOrderIdAndType(inventory.getId(), orderId, InventoryMovementType.RESTORATION);

        if (previous.isPresent()) {
            return existingMovementResponse(inventory, previous.get(), quantity);
        }

        InventoryMovement deduction = movementRepository.findByInventoryIdAndOrderIdAndType(inventory.getId(), orderId, InventoryMovementType.DEDUCTION)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "해당 주문에 대한 재고 차감 기록이 없습니다."));

        if (!deduction.matches(quantity)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "차감한 수량과 복원하려는 수량이 다릅니다.");
        }

        try {
            inventory.restore(quantity, userId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }

        movementRepository.save(new InventoryMovement(inventory, orderId, InventoryMovementType.RESTORATION, quantity, userId));
        return new InventoryResponse(inventory);
    }

    private Inventory findLockedInventory(UUID hubId, UUID productId) {
        return inventoryRepository.findLockedByHubIdAndProductIdAndDeletedAtIsNull(hubId, productId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "최초 재고 등록이 필요합니다."));
    }

    private InventoryResponse existingMovementResponse(
            Inventory inventory,
            InventoryMovement previous,
            Integer quantity
    ) {
        if (!previous.matches(quantity)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "동일한 주문 ID를 다른 수량으로 사용할 수 없습니다.");
        }
        return new InventoryResponse(inventory, previous.getResultQuantity());
    }
}

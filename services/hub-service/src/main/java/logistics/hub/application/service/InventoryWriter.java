package logistics.hub.application.service;

import logistics.hub.domain.entity.Hub;
import logistics.hub.domain.entity.Inventory;
import logistics.hub.infrastructure.repository.HubRepository;
import logistics.hub.infrastructure.repository.InventoryRepository;
import logistics.hub.presentation.dto.response.InventoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryWriter {

    private final HubRepository hubRepository;
    private final InventoryRepository inventoryRepository;

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
}
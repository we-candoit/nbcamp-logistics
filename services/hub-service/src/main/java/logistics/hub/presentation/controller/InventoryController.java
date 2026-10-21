package logistics.hub.presentation.controller;

import jakarta.validation.Valid;
import logistics.hub.application.service.InventoryRegistrationService;
import logistics.hub.application.service.InventoryRestockService;
import logistics.hub.application.service.InventoryService;
import logistics.hub.presentation.dto.request.InventoryCreateRequest;
import logistics.hub.presentation.dto.request.InventoryRestockRequest;
import logistics.hub.presentation.dto.response.InventoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/hubs/{hubId}/items")
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryRestockService inventoryRestockService;
    private final InventoryRegistrationService inventoryRegistrationService;

    @GetMapping
    public List<InventoryResponse> getInventories(@PathVariable("hubId") UUID hubId) {
        return inventoryService.getInventories(hubId);
    }

    @GetMapping("/{itemId}")
    public InventoryResponse getInventory(@PathVariable("hubId") UUID hubId, @PathVariable("itemId") UUID productId) {
        return inventoryService.getInventory(hubId, productId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryResponse createInventory(
            @PathVariable("hubId") UUID hubId,
            @Valid @RequestBody InventoryCreateRequest request,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-Role-Name", required = false) String role
    ) {
        return inventoryRegistrationService.createInventory(
                hubId,
                request,
                userId,
                role
        );
    }

    @PatchMapping("/{itemId}")
    public InventoryResponse restock(
            @PathVariable("hubId") UUID hubId,
            @PathVariable("itemId") UUID productId,
            @Valid @RequestBody InventoryRestockRequest request,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-Role-Name", required = false) String role
    ) {
        return inventoryRestockService.restock(
                hubId,
                productId,
                request,
                userId,
                role
        );
    }
}

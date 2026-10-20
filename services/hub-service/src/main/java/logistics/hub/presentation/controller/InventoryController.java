package logistics.hub.presentation.controller;

import jakarta.validation.Valid;
import logistics.hub.application.service.InventoryRestockService;
import logistics.hub.application.service.InventoryService;
import logistics.hub.presentation.dto.request.InventoryRestockRequest;
import logistics.hub.presentation.dto.response.InventoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/hubs/{hubId}/items")
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryRestockService inventoryRestockService;

    @GetMapping
    public List<InventoryResponse> getInventories(@PathVariable("hubId") UUID hubId) {
        return inventoryService.getInventories(hubId);
    }

    @GetMapping("/{itemId}")
    public InventoryResponse getInventory(@PathVariable("hubId") UUID hubId, @PathVariable("itemId") UUID productId) {
        return inventoryService.getInventory(hubId, productId);
    }

    @PatchMapping("/{itemId}")
    public InventoryResponse restock(
            @PathVariable("hubId") UUID hubId,
            @PathVariable("itemId") UUID productId,
            @Valid @RequestBody InventoryRestockRequest request,
            @RequestAttribute(value = "userId", required = false) UUID userId,
            @RequestAttribute(value = "role", required = false) String role,
            @RequestHeader(value = "Authorization", required = false)
            String authorization
    ) {
        return inventoryRestockService.restock(
                hubId,
                productId,
                request,
                userId,
                role,
                authorization
        );
    }
}
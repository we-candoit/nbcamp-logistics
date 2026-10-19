package logistics.hub.presentation.controller;

import logistics.hub.application.service.InventoryService;
import logistics.hub.presentation.dto.response.InventoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/hubs/{hubId}/items")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping
    public List<InventoryResponse> getInventories(@PathVariable("hubId") UUID hubId) {
        return inventoryService.getInventories(hubId);
    }

    @GetMapping("/{itemId}")
    public InventoryResponse getInventory(@PathVariable("hubId") UUID hubId, @PathVariable("itemId") UUID productId) {
        return inventoryService.getInventory(hubId, productId);
    }
}
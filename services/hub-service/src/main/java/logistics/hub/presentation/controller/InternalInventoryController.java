package logistics.hub.presentation.controller;

import logistics.hub.application.service.InventoryMovementService;
import logistics.hub.presentation.dto.response.InventoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal")
public class InternalInventoryController {

    private final InventoryMovementService movementService;

    @PostMapping("/hubs/{hubId}/items/{itemId}/deductions")
    public InventoryResponse deduct(
            @PathVariable("hubId") UUID hubId,
            @PathVariable("itemId") UUID itemId,
            @RequestParam("orderId") UUID orderId,
            @RequestParam("quantity") Long quantity,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId
    ) {
        return movementService.deduct(hubId, itemId, orderId, quantity, userId);
    }

    @PostMapping({"/hubs/{hubId}/items/{itemId}/restorations"})
    public InventoryResponse restore(
            @PathVariable("hubId") UUID hubId,
            @PathVariable("itemId") UUID itemId,
            @RequestParam("orderId") UUID orderId,
            @RequestParam("quantity") Long quantity,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId
    ) {
        return movementService.restore(hubId, itemId, orderId, quantity, userId);
    }
}

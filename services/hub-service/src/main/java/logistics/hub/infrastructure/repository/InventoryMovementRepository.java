package logistics.hub.infrastructure.repository;

import logistics.hub.domain.entity.InventoryMovement;
import logistics.hub.domain.enums.InventoryMovementType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, UUID> {

    Optional<InventoryMovement> findByInventoryIdAndOrderIdAndType(UUID inventoryId, UUID orderId, InventoryMovementType type);}

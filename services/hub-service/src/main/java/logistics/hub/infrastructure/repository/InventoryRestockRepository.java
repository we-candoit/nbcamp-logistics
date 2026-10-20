package logistics.hub.infrastructure.repository;

import logistics.hub.domain.entity.InventoryRestock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InventoryRestockRepository extends JpaRepository<InventoryRestock, UUID> {

    Optional<InventoryRestock> findByInventoryIdAndRequestId(UUID inventoryId, UUID requestId);
}

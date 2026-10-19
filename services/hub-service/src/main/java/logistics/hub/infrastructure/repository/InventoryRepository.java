package logistics.hub.infrastructure.repository;

import logistics.hub.domain.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    List<Inventory> findAllByHubIdAndDeletedAtIsNull(UUID hubId);

    Optional<Inventory> findByHubIdAndProductIdAndDeletedAtIsNull(UUID hubId, UUID productId);

    boolean existsByHubIdAndProductId(UUID hubId, UUID productId);
}
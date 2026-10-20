package logistics.hub.infrastructure.repository;

import jakarta.persistence.LockModeType;
import logistics.hub.domain.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    List<Inventory> findAllByHubIdAndDeletedAtIsNull(UUID hubId);

    Optional<Inventory> findByHubIdAndProductIdAndDeletedAtIsNull(UUID hubId, UUID productId);

    boolean existsByHubIdAndProductId(UUID hubId, UUID productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Inventory> findLockedByHubIdAndProductIdAndDeletedAtIsNull(UUID hubId, UUID productId);
}
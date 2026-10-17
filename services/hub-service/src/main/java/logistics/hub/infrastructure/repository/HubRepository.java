package logistics.hub.infrastructure.repository;

import logistics.hub.domain.entity.Hub;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HubRepository extends JpaRepository<Hub, UUID> {

    boolean existsByName(String name);

    List<Hub> findAllByDeletedAtIsNullOrderByNameAsc();

    Optional<Hub> findByIdAndDeletedAtIsNull(UUID id);

}
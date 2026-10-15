package logistics.hub.infrastructure.repository;

import logistics.hub.domain.entity.HubRoute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HubRouteRepository
        extends JpaRepository<HubRoute, UUID> {

    List<HubRoute> findAllByDeletedAtIsNull();

    Optional<HubRoute> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByOriginHub_IdAndDestinationHub_Id(
            UUID originHubId,
            UUID destinationHubId
    );
}
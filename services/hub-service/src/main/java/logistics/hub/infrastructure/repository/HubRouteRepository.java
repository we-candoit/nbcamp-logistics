package logistics.hub.infrastructure.repository;

import logistics.hub.domain.entity.HubRoute;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HubRouteRepository
        extends JpaRepository<HubRoute, UUID> {

    // EntityGraph : 경로 조회 시 출발 허브와 도착 허브를 함께 가져옴
    @EntityGraph(attributePaths = {"originHub", "destinationHub"})
    List<HubRoute> findAllByDeletedAtIsNull();

    @EntityGraph(attributePaths = {"originHub", "destinationHub"})
    Optional<HubRoute> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByOriginHub_IdAndDestinationHub_Id(
            UUID originHubId,
            UUID destinationHubId
    );

    Optional<HubRoute> findByOriginHub_IdAndDestinationHub_IdAndDeletedAtIsNull(
            UUID originHubId,
            UUID destinationHubId
    );
}
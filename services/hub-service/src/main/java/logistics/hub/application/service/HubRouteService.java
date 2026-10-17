package logistics.hub.application.service;

import logistics.hub.domain.entity.HubRoute;
import logistics.hub.infrastructure.repository.HubRouteRepository;
import logistics.hub.presentation.dto.response.HubRouteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HubRouteService {

    private final HubRouteRepository hubRouteRepository;

    public List<HubRouteResponse> getHubRoutes() {
        List<HubRoute> routes = hubRouteRepository.findAllByDeletedAtIsNull();

        return routes.stream()
                .map(HubRouteResponse::new)
                .toList();
    }

    public HubRouteResponse getHubRoute(UUID routeId) {
        HubRoute route = hubRouteRepository.findByIdAndDeletedAtIsNull(routeId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "해당 허브 이동정보를 찾을 수 없습니다."
                                )
                        );

        return new HubRouteResponse(route);
    }

    public HubRouteResponse getRouteByHubs(
            UUID originHubId,
            UUID destinationHubId
    ) {
        HubRoute route = hubRouteRepository
                .findByOriginHub_IdAndDestinationHub_IdAndDeletedAtIsNull(
                        originHubId,
                        destinationHubId
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "해당 허브 이동경로를 찾을 수 없습니다."
                ));

        return new HubRouteResponse(route);
    }
}

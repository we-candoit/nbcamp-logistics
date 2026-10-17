package logistics.hub.presentation.dto.response;

import logistics.hub.domain.entity.HubRoute;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
public class HubRouteResponse {

    private final UUID id;

    private final UUID originHubId;
    private final String originHubName;

    private final UUID destinationHubId;
    private final String destinationHubName;

    private final BigDecimal distance;
    private final Integer duration;

    public HubRouteResponse(HubRoute route) {
        this.id = route.getId();

        this.originHubId = route.getOriginHub().getId();
        this.originHubName = route.getOriginHub().getName();

        this.destinationHubId = route.getDestinationHub().getId();
        this.destinationHubName = route.getDestinationHub().getName();

        this.distance = route.getDistance();
        this.duration = route.getDuration();
    }
}
package logistics.hub.presentation.dto.response;

import logistics.hub.domain.entity.Hub;
import lombok.Getter;

import java.util.UUID;

@Getter
public class HubCheckResponse {

    private final UUID hubId;
    private final String hubName;

    public HubCheckResponse(Hub hub) {
        this.hubId = hub.getId();
        this.hubName = hub.getName();
    }
}

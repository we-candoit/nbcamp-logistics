package logistics.hub.presentation.dto.response;

import logistics.hub.domain.entity.Hub;
import lombok.Getter;

import java.util.UUID;

@Getter
public class HubResponse {

    private final UUID id;
    private final String name;
    private final String address;

    public HubResponse(Hub hub) {
        this.id = hub.getId();
        this.name = hub.getName();
        this.address = hub.getAddress();
    }
}
package logistics.user.infrastructure.client;

import lombok.Getter;

import java.util.UUID;

@Getter
public class HubResponse {

    private UUID hubId;
    private String hubName;
}
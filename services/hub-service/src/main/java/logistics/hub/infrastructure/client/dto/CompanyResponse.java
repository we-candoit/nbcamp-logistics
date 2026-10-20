package logistics.hub.infrastructure.client.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class CompanyResponse {

    private UUID id;
    private UUID hubId;
}
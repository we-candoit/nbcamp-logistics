package logistics.hub.infrastructure.client.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class ProductResponse {

    private UUID id;
    private UUID companyId;
}
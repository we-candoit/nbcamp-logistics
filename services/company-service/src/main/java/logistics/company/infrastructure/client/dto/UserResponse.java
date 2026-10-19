package logistics.company.infrastructure.client.dto;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserResponse {
    private UUID userId;
    private String affiliationType;    // HUB, COMPANY
    private String affiliationStatus;  // PENDING, CONFIRMED
}

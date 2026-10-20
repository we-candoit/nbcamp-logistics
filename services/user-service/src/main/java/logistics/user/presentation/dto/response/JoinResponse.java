package logistics.user.presentation.dto.response;

import logistics.user.domain.entity.User;
import logistics.user.domain.enums.AffiliationStatus;
import logistics.user.domain.enums.AffiliationType;
import lombok.Getter;

import java.util.UUID;

@Getter
public class JoinResponse {
    private final UUID id;
    private final String loginId;
    private final String name;
    private final AffiliationType affiliationType;
    private final AffiliationStatus affiliationStatus;

    public JoinResponse(User user) {
        this.id = user.getId();
        this.loginId = user.getLoginId();
        this.name = user.getName();
        this.affiliationType = user.getAffiliationType();
        this.affiliationStatus = user.getAffiliationStatus();
    }
}
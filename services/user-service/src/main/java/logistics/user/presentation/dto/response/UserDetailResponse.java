package logistics.user.presentation.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import logistics.user.domain.entity.User;
import logistics.user.domain.enums.AffiliationStatus;
import logistics.user.domain.enums.AffiliationType;
import logistics.user.domain.enums.UserRole;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class UserDetailResponse {

    private final UUID userId;
    private final String loginId;
    private final String name;
    private final String email;
    private final String slackId;
    private final UserRole role;
    // Lombok getter(isDeliveryManager())를 Jackson이 "deliveryManager"로 바꾸지 않게 키를 고정
    @JsonProperty("isDeliveryManager")
    private final boolean isDeliveryManager;
    private final AffiliationType affiliationType;
    private final String affiliationName;
    private final AffiliationStatus affiliationStatus;
    private final UUID hubId;
    private final UUID companyId;
    private final LocalDateTime createdAt;

    public UserDetailResponse(User user) {
        this.userId = user.getId();
        this.loginId = user.getLoginId();
        this.name = user.getName();
        this.email = user.getEmail();
        this.slackId = user.getSlackId();
        this.role = user.getRole();
        this.isDeliveryManager = user.isDeliveryManager();
        this.affiliationType = user.getAffiliationType();
        this.affiliationName = user.getAffiliationName();
        this.affiliationStatus = user.getAffiliationStatus();
        this.hubId = user.getHubId();
        this.companyId = user.getCompanyId();
        this.createdAt = user.getCreatedAt();
    }
}
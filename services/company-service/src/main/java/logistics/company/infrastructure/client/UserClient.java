package logistics.company.infrastructure.client;

import logistics.company.infrastructure.client.dto.UserAffiliationReleaseRequest;
import logistics.company.infrastructure.client.dto.UserAffiliationUpdateRequest;
import logistics.company.infrastructure.client.dto.UserApiResponse;
import logistics.company.infrastructure.client.dto.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "user-service")
public interface UserClient {
    @GetMapping("/api/users/{userId}")
    UserApiResponse<UserResponse> getUser(@PathVariable("userId") UUID userId);

    @PatchMapping("/internal/users/{userId}/affiliation")
    void updateAffiliation(@PathVariable("userId") UUID userId,
                           @RequestBody UserAffiliationUpdateRequest request);

    @PatchMapping("/internal/users/{userId}/affiliation/release")
    void releaseAffiliation(@PathVariable("userId") UUID userId,
                           @RequestBody UserAffiliationReleaseRequest request);
}

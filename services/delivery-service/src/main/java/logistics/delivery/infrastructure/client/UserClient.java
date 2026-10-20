package logistics.delivery.infrastructure.client;

import logistics.delivery.infrastructure.client.dto.UserApiResponse;
import logistics.delivery.infrastructure.client.dto.UserDetailResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

@FeignClient(name = "user-service")
public interface UserClient {

    @GetMapping("/api/users/{id}")
    UserApiResponse<UserDetailResponse> getUserDetail(
            @PathVariable("id") UUID userId,                 // 배정할 배송 담당자id
            @RequestHeader("X-User-Id") UUID requesterId,    // 배정을 요청한 MASTER의 id
            @RequestHeader("X-Role-Name") String role
    );
}

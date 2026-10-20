package logistics.order.infrastructure.client;

import logistics.order.infrastructure.client.dto.FetchUserByIdResponse;
import logistics.order.infrastructure.client.dto.UserApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "${USER_SERVICE_NAME}")
public interface UserServiceClient {

    @GetMapping("/api/users/{id}")
    UserApiResponse<FetchUserByIdResponse> fetchUserById(@PathVariable("id") UUID id);

}

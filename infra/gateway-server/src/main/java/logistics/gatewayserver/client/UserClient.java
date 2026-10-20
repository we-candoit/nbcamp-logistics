package logistics.gatewayserver.client;

import logistics.gatewayserver.common.GatewayHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Component
public class UserClient {

    private final WebClient webClient;

    public UserClient(WebClient.Builder webClientBuilder,
                      @Value("${USER_SERVICE_NAME}") String userServiceName) {
        this.webClient = webClientBuilder.baseUrl("lb://" + userServiceName).build();
    }

    // DB에 저장된 사용자 role 조회. 없거나 삭제된 사용자면 빈 값
    public Mono<String> findRole(String userId, String role) {
        return webClient.get()
                .uri("/api/users/{userId}", userId)
                // X-Internal-Secret 확정 전까지는 토큰의 본인 정보로 "본인 조회"를 한다
                .header(GatewayHeaders.USER_ID, userId)
                .header(GatewayHeaders.ROLE_NAME, role)
                .retrieve()
                .bodyToMono(UserApiResponse.class)
                .map(response -> response.getData().getRole())
                .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty())
                .onErrorMap(e -> new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "사용자 정보를 확인할 수 없습니다.", e));
    }
}
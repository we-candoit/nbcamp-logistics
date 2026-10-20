package logistics.gatewayserver.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import logistics.gatewayserver.client.UserClient;
import logistics.gatewayserver.common.GatewayHeaders;
import logistics.gatewayserver.jwt.JwtValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final List<String> WHITE_LIST = List.of("/api/auth/join", "/api/auth/login");
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtValidator jwtValidator;
    private final UserClient userClient;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1. 클라이언트가 보낸 사용자/내부 헤더 제거 (헤더 위조 방지)
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(GatewayHeaders.USER_ID);
                    headers.remove(GatewayHeaders.ROLE_NAME);
                    headers.remove(GatewayHeaders.INTERNAL_SECRET);
                })
                .build();
        String path = request.getPath().value();

        // 2. 내부 API는 외부에서 호출할 수 없음 (라우팅 설정 실수 대비)
        if (path.startsWith("/internal/")) {
            return reject(exchange, HttpStatus.FORBIDDEN);
        }

        // 3. 회원가입, 로그인은 토큰 없이 통과
        if (WHITE_LIST.contains(path)) {
            return chain.filter(exchange.mutate().request(request).build());
        }

        // 4. JWT 검증 (없음, 서명 오류, 만료 → 401)
        String token = resolveToken(request);
        if (token == null) {
            return reject(exchange, HttpStatus.UNAUTHORIZED);
        }
        Claims claims;
        try {
            claims = jwtValidator.extractClaims(token);
        } catch (JwtException | IllegalArgumentException e) {
            return reject(exchange, HttpStatus.UNAUTHORIZED);
        }
        String userId = claims.getSubject();
        String role = claims.get("role", String.class);
        if (userId == null || role == null) {
            return reject(exchange, HttpStatus.UNAUTHORIZED);
        }

        // 5. 요청마다 DB에 저장된 role과 비교 (발제: 권한은 요청마다 저장된 값과 동일한지 체크
        return userClient.findRole(userId, role)
                .map(savedRole -> savedRole.equals(role))
                .defaultIfEmpty(false)
                .flatMap(valid -> {
                    if (!valid) {
                        return reject(exchange, HttpStatus.UNAUTHORIZED);
                    }
                    // 6. 검증된 값으로만 헤더를 추가해서 라우팅
                    ServerHttpRequest authenticated = request.mutate()
                            .header(GatewayHeaders.USER_ID, userId)
                            .header(GatewayHeaders.ROLE_NAME, role)
                            .build();
                    return chain.filter(exchange.mutate().request(authenticated).build());
                });
    }

    private String resolveToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authorization.substring(BEARER_PREFIX.length());
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }

    // 다른 Gateway 필터보다 먼저 실행되도록
    @Override
    public int getOrder() {
        return -1;
    }
}
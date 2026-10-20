package logistics.delivery;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import logistics.delivery.common.exception.DeliveryErrorCode;
import logistics.delivery.common.exception.DeliveryException;
import logistics.delivery.infrastructure.client.HubClientAdapter;
import logistics.delivery.infrastructure.client.UserClientAdapter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 Feign 클라이언트를 로컬 HTTP 서버에 연결해 URL, 헤더, JSON 변환을 검증한다.
 * 실제 허브·사용자 서비스의 실행 및 Eureka 등록은 검증하지 않는다.
 */
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=jdbc:h2:mem:delivery-client-contract;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "eureka.client.enabled=false",
        "eureka.client.service-url.defaultZone=http://localhost:8761/eureka/",
        "spring.cloud.discovery.enabled=false"
})
class DeliveryClientContractTest {

    private static final HttpServer SERVER = startServer();
    private static volatile String responseBody;
    private static volatile CapturedRequest captured;

    @Autowired
    private HubClientAdapter hubClientAdapter;

    @Autowired
    private UserClientAdapter userClientAdapter;

    @DynamicPropertySource
    static void configureClients(DynamicPropertyRegistry registry) {
        String url = "http://127.0.0.1:" + SERVER.getAddress().getPort();
        registry.add("spring.cloud.openfeign.client.config.hub-service.url", () -> url);
        registry.add("spring.cloud.openfeign.client.config.user-service.url", () -> url);
    }

    @BeforeEach
    void resetResponse() {
        responseBody = "{}";
        captured = null;
    }

    @AfterAll
    static void stopServer() {
        SERVER.stop(0);
    }

    @Test
    void sendsHubPairToInternalRouteEndpointAndDecodesSingleRoute() {
        UUID origin = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        responseBody = """
                {"id":"%s","originHubId":"%s","originHubName":"origin",
                 "destinationHubId":"%s","destinationHubName":"destination",
                 "distance":12.5,"duration":30}
                """.formatted(UUID.randomUUID(), origin, destination);

        var route = hubClientAdapter.getRoute(origin, destination);

        assertThat(captured.method()).isEqualTo("GET");
        assertThat(captured.path()).isEqualTo("/internal/hubs/routes/hubs");
        assertThat(captured.query().split("&")).containsExactlyInAnyOrder(
                "originHubId=" + origin, "destinationHubId=" + destination);
        assertThat(route.originHubId()).isEqualTo(origin);
        assertThat(route.destinationHubId()).isEqualTo(destination);
        assertThat(route.distance()).isEqualByComparingTo("12.5");
        assertThat(route.duration()).isEqualTo(30);
    }

    @Test
    void forwardsRequesterHeadersAndDecodesNestedUserData() {
        UUID manager = UUID.randomUUID();
        UUID requester = UUID.randomUUID();
        UUID hub = UUID.randomUUID();
        responseBody = """
                {"success":true,"message":null,"data":{
                  "userId":"%s","isDeliveryManager":true,
                  "affiliationType":"HUB","affiliationStatus":"CONFIRMED",
                  "hubId":"%s","loginId":"manager","role":"USER"}}
                """.formatted(manager, hub);

        var user = userClientAdapter.getAssignmentCandidate(manager, requester, "MASTER");

        assertThat(captured.method()).isEqualTo("GET");
        assertThat(captured.path()).isEqualTo("/api/users/" + manager);
        assertThat(captured.requesterId()).isEqualTo(requester.toString());
        assertThat(captured.role()).isEqualTo("MASTER");
        assertThat(user.userId()).isEqualTo(manager);
        assertThat(user.isDeliveryManager()).isTrue();
        assertThat(user.hubId()).isEqualTo(hub);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"success\":false,\"message\":\"failed\",\"data\":null}",
            "{\"message\":null,\"data\":null}",
            "{\"success\":true,\"data\":null}"
    })
    void rejectsUnsuccessfulOrIncompleteUserJson(String body) {
        responseBody = body;

        assertThatThrownBy(() -> userClientAdapter.getAssignmentCandidate(
                UUID.randomUUID(), UUID.randomUUID(), "MASTER"))
                .isInstanceOfSatisfying(DeliveryException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(DeliveryErrorCode.INVALID_USER_SERVICE_RESPONSE));
    }

    private static HttpServer startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", DeliveryClientContractTest::respond);
            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void respond(HttpExchange exchange) throws IOException {
        try {
            captured = new CapturedRequest(exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(), exchange.getRequestURI().getRawQuery(),
                    exchange.getRequestHeaders().getFirst("X-User-Id"),
                    exchange.getRequestHeaders().getFirst("X-Role-Name"));
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }

    private record CapturedRequest(String method, String path, String query,
                                   String requesterId, String role) {
    }
}

package logistics.delivery;

import com.jayway.jsonpath.JsonPath;
import feign.FeignException;
import feign.Request;
import feign.Response;
import logistics.delivery.infrastructure.client.HubClient;
import logistics.delivery.infrastructure.client.OrderClient;
import logistics.delivery.infrastructure.client.dto.OrderDetailResponse;
import logistics.delivery.infrastructure.repository.DeliveryRepository;
import logistics.delivery.infrastructure.repository.DeliveryRouteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 실제 HTTP 요청으로 배송 Controller·인가 로직·테스트 H2 DB 조회를 검증한다.
 *
 * 중요: OrderClient는 Mockito 가짜 응답으로 대체한다. 실제 주문 서비스의 기동,
 * Eureka/Feign 네트워크 연결, 헤더 전송·상대측 인증, JSON 역직렬화는 검증하지 않는다.
 * 주문자 조회의 실제 서비스 간 연동은 INTEGRATION_CHECKLIST.md에 남긴 후속 작업이다.
 * 테스트가 통과해도 실제 주문 서비스 연동 완료로 간주하지 않는다.
 */
@ActiveProfiles("test")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:delivery-detail-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "eureka.client.enabled=false",
                "eureka.client.service-url.defaultZone=http://localhost:8761/eureka/",
                "spring.cloud.discovery.enabled=false"
        }
)
class DeliveryDetailIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private DeliveryRouteRepository deliveryRouteRepository;

    @Autowired
    private JdbcTemplate jdbc;

    // 실제 주문 서버는 호출하지 않는다. 배송 측 판단 로직만 검증한다.
    @MockitoBean
    private OrderClient orderClient;

    @MockitoBean
    private HubClient hubClient;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    private UUID deliveryId;
    private UUID orderId;
    private UUID userId;

    @BeforeEach
    void prepareDelivery() throws Exception {
        deliveryRouteRepository.deleteAllInBatch();
        deliveryRepository.deleteAllInBatch();
        orderId = UUID.randomUUID();
        userId = UUID.randomUUID();
        UUID hubId = UUID.randomUUID();
        String body = """
                {
                  "orderId": "%s",
                  "departureHubId": "%s",
                  "destinationHubId": "%s",
                  "address": "서울 테스트 주소",
                  "recipientName": "테스트 수령인",
                  "recipientPhone": "010-1234-5678"
                }
                """.formatted(orderId, hubId, hubId);
        HttpRequest request = request("/internal/deliveries")
                .header("X-User-Name", "createuser")
                .header("X-Role-Name", "USER")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        HttpResponse<String> response = send(request);
        assertThat(response.statusCode()).isEqualTo(201);
        deliveryId = UUID.fromString(JsonPath.read(response.body(), "$.deliveryId"));
    }

    @Test
    @DisplayName("MASTER는 주문 서비스 호출 없이 상세 조회한다")
    void masterCanRead() throws Exception {
        assertDetail(get(deliveryId, userId.toString(), "reader", "MASTER"));
        verifyNoInteractions(orderClient);
    }

    @Test
    @DisplayName("배송 전체 담당자는 주문 서비스 호출 없이 상세 조회한다")
    void deliveryManagerCanRead() throws Exception {
        jdbc.update("UPDATE p_deliveries SET manager_id = ? WHERE id = ?", userId, deliveryId);
        assertDetail(get(deliveryId, userId.toString(), "reader", "USER"));
        verifyNoInteractions(orderClient);
    }

    @Test
    @DisplayName("전체 담당자가 아니어도 소속 경로 담당자이면 상세 조회한다")
    void routeManagerCanRead() throws Exception {
        jdbc.update("UPDATE p_deliveries SET manager_id = ? WHERE id = ?", UUID.randomUUID(), deliveryId);
        jdbc.update("UPDATE p_delivery_routes SET manager_id = ? WHERE delivery_id = ?", userId, deliveryId);
        assertDetail(get(deliveryId, userId.toString(), "reader", "USER"));
        verifyNoInteractions(orderClient);
    }

    @Test
    @DisplayName("가짜 주문 응답의 주문자가 요청자와 같으면 상세 조회한다")
    void orderOwnerCanReadUsingMockResponse() throws Exception {
        when(orderClient.getOrderDetail(orderId, userId, "reader", "USER"))
                .thenReturn(new OrderDetailResponse(orderId, userId));
        assertDetail(get(deliveryId, userId.toString(), "reader", "USER"));
        // 메서드에 원 요청자 정보를 전달하는지 검증한다. 실제 HTTP 헤더 전송 검증은 아니다.
        verify(orderClient).getOrderDetail(orderId, userId, "reader", "USER");
    }

    @Test
    @DisplayName("가짜 주문 응답의 주문자가 다른 사용자인 경우 404로 숨긴다")
    void otherUserCannotRead() throws Exception {
        when(orderClient.getOrderDetail(orderId, userId, "reader", "USER"))
                .thenReturn(new OrderDetailResponse(orderId, UUID.randomUUID()));
        assertThat(get(deliveryId, userId.toString(), "reader", "USER").statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("삭제된 경로의 담당 관계만으로는 조회를 허용하지 않는다")
    void deletedRouteDoesNotGrantAccess() throws Exception {
        jdbc.update("UPDATE p_delivery_routes SET manager_id = ?, deleted_at = CURRENT_TIMESTAMP, deleted_by = 'test' WHERE delivery_id = ?",
                userId, deliveryId);
        when(orderClient.getOrderDetail(orderId, userId, "reader", "USER"))
                .thenReturn(new OrderDetailResponse(orderId, UUID.randomUUID()));
        assertThat(get(deliveryId, userId.toString(), "reader", "USER").statusCode()).isEqualTo(404);
        verify(orderClient).getOrderDetail(orderId, userId, "reader", "USER");
    }

    @Test
    @DisplayName("존재하지 않는 배송은 MASTER에게도 404를 반환한다")
    void missingDeliveryReturns404() throws Exception {
        assertThat(get(UUID.randomUUID(), userId.toString(), "reader", "MASTER").statusCode()).isEqualTo(404);
        verifyNoInteractions(orderClient);
    }

    @Test
    @DisplayName("논리 삭제된 배송은 MASTER에게도 404를 반환한다")
    void deletedDeliveryReturns404() throws Exception {
        jdbc.update("UPDATE p_deliveries SET deleted_at = CURRENT_TIMESTAMP, deleted_by = 'test' WHERE id = ?", deliveryId);
        assertThat(get(deliveryId, userId.toString(), "reader", "MASTER").statusCode()).isEqualTo(404);
        verifyNoInteractions(orderClient);
    }

    @ParameterizedTest
    @CsvSource({"403,404", "404,404", "401,503", "500,503"})
    @DisplayName("가짜 주문 호출 오류를 배송 조회 오류로 변환한다")
    void mapsMockOrderFailures(int orderStatus, int expectedStatus) throws Exception {
        Request request = Request.create(Request.HttpMethod.GET, "/api/orders/" + orderId,
                Map.of(), new byte[0], StandardCharsets.UTF_8, null);
        FeignException failure = FeignException.errorStatus("getOrderDetail",
                Response.builder().request(request).status(orderStatus).reason("test failure").build());
        when(orderClient.getOrderDetail(orderId, userId, "reader", "USER")).thenThrow(failure);
        assertThat(get(deliveryId, userId.toString(), "reader", "USER").statusCode()).isEqualTo(expectedStatus);
    }

    @ParameterizedTest
    @ValueSource(strings = {"empty", "missingOrderId", "wrongOrderId", "missingOwnerId"})
    @DisplayName("가짜 주문 응답이 불완전하면 소유권 확인 불가로 503을 반환한다")
    void rejectsIncompleteMockOrderResponse(String scenario) throws Exception {
        OrderDetailResponse order = switch (scenario) {
            case "empty" -> null;
            case "missingOrderId" -> new OrderDetailResponse(null, userId);
            case "wrongOrderId" -> new OrderDetailResponse(UUID.randomUUID(), userId);
            default -> new OrderDetailResponse(orderId, null);
        };
        when(orderClient.getOrderDetail(orderId, userId, "reader", "USER")).thenReturn(order);
        assertThat(get(deliveryId, userId.toString(), "reader", "USER").statusCode()).isEqualTo(503);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "NULL|reader|MASTER|401",
            "invalid-id|reader|MASTER|400",
            "VALID|NULL|MASTER|401",
            "VALID|reader|NULL|401",
            "VALID|reader|INVALID|403"
    }, delimiter = '|')
    @DisplayName("필수 헤더와 사용자 UUID·역할을 검증하고 주문 서비스는 호출하지 않는다")
    void validatesHeaders(String id, String username, String role, int expectedStatus) throws Exception {
        String actualId = "VALID".equals(id) ? userId.toString() : id;
        assertThat(get(deliveryId, actualId, username, role).statusCode()).isEqualTo(expectedStatus);
        verifyNoInteractions(orderClient);
    }

    private HttpResponse<String> get(UUID id, String userId, String username, String role) throws Exception {
        HttpRequest.Builder builder = request("/api/deliveries/" + id).GET();
        if (!"NULL".equals(userId)) builder.header("X-User-Id", userId);
        if (!"NULL".equals(username)) builder.header("X-User-Name", username);
        if (!"NULL".equals(role)) builder.header("X-Role-Name", role);
        return send(builder.build());
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10));
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private void assertDetail(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat((String) JsonPath.read(response.body(), "$.deliveryId")).isEqualTo(deliveryId.toString());
        assertThat((String) JsonPath.read(response.body(), "$.orderId")).isEqualTo(orderId.toString());
        assertThat((String) JsonPath.read(response.body(), "$.address")).isEqualTo("서울 테스트 주소");
        assertThat((String) JsonPath.read(response.body(), "$.recipientName")).isEqualTo("테스트 수령인");
        assertThat((String) JsonPath.read(response.body(), "$.recipientPhone")).isEqualTo("010-1234-5678");
    }
}

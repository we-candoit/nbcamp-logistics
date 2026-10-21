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
                "spring.datasource.url=jdbc:h2:mem:delivery-route-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "eureka.client.enabled=false",
                "eureka.client.service-url.defaultZone=http://localhost:8761/eureka/",
                "spring.cloud.discovery.enabled=false"
        }
)
class DeliveryRouteIntegrationTest {

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
                .header("X-User-Id", UUID.nameUUIDFromBytes("createuser".getBytes(StandardCharsets.UTF_8)).toString())
                .header("X-Role-Name", "USER")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        HttpResponse<String> response = send(request);
        assertThat(response.statusCode()).isEqualTo(201);
        deliveryId = UUID.fromString(JsonPath.read(response.body(), "$.deliveryId"));
    }

    @Test
    @DisplayName("MASTER는 주문 서비스 호출 없이 배송 경로 조회한다")
    void masterCanRead() throws Exception {
        assertDetail(get(deliveryId, userId.toString(), "reader", "MASTER"));
        verifyNoInteractions(orderClient);
    }

    @Test
    @DisplayName("배송 전체 담당자는 주문 서비스 호출 없이 배송 경로 조회한다")
    void deliveryManagerCanRead() throws Exception {
        jdbc.update("UPDATE p_deliveries SET manager_id = ? WHERE id = ?", userId, deliveryId);
        assertDetail(get(deliveryId, userId.toString(), "reader", "USER"));
        verifyNoInteractions(orderClient);
    }

    @Test
    @DisplayName("전체 담당자가 아니어도 소속 경로 담당자이면 배송 경로 조회한다")
    void routeManagerCanRead() throws Exception {
        jdbc.update("UPDATE p_deliveries SET manager_id = ? WHERE id = ?", UUID.randomUUID(), deliveryId);
        jdbc.update("UPDATE p_delivery_routes SET manager_id = ? WHERE delivery_id = ?", userId, deliveryId);
        assertDetail(get(deliveryId, userId.toString(), "reader", "USER"));
        verifyNoInteractions(orderClient);
    }

    @Test
    @DisplayName("가짜 주문 응답의 주문자가 요청자와 같으면 배송 경로 조회한다")
    void orderOwnerCanReadUsingMockResponse() throws Exception {
        when(orderClient.getOrderDetail(orderId, userId, "USER"))
                .thenReturn(new OrderDetailResponse(orderId, userId));
        assertDetail(get(deliveryId, userId.toString(), "reader", "USER"));
        // 메서드에 원 요청자 정보를 전달하는지 검증한다. 실제 HTTP 헤더 전송 검증은 아니다.
        verify(orderClient).getOrderDetail(orderId, userId, "USER");
    }

    @Test
    @DisplayName("가짜 주문 응답의 주문자가 다른 사용자인 경우 404로 숨긴다")
    void otherUserCannotRead() throws Exception {
        when(orderClient.getOrderDetail(orderId, userId, "USER"))
                .thenReturn(new OrderDetailResponse(orderId, UUID.randomUUID()));
        assertThat(get(deliveryId, userId.toString(), "reader", "USER").statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("삭제된 경로의 담당 관계만으로는 조회를 허용하지 않는다")
    void deletedRouteDoesNotGrantAccess() throws Exception {
        jdbc.update("UPDATE p_delivery_routes SET manager_id = ?, deleted_at = CURRENT_TIMESTAMP, deleted_by = '00000000-0000-4000-8000-000000000001' WHERE delivery_id = ?",
                userId, deliveryId);
        when(orderClient.getOrderDetail(orderId, userId, "USER"))
                .thenReturn(new OrderDetailResponse(orderId, UUID.randomUUID()));
        assertThat(get(deliveryId, userId.toString(), "reader", "USER").statusCode()).isEqualTo(404);
        verify(orderClient).getOrderDetail(orderId, userId, "USER");
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
        jdbc.update("UPDATE p_deliveries SET deleted_at = CURRENT_TIMESTAMP, deleted_by = '00000000-0000-4000-8000-000000000001' WHERE id = ?", deliveryId);
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
        when(orderClient.getOrderDetail(orderId, userId, "USER")).thenThrow(failure);
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
        when(orderClient.getOrderDetail(orderId, userId, "USER")).thenReturn(order);
        assertThat(get(deliveryId, userId.toString(), "reader", "USER").statusCode()).isEqualTo(503);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "NULL|reader|MASTER|401",
            "invalid-id|reader|MASTER|400",
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
        HttpRequest.Builder builder = request("/api/deliveries/" + id + "/routes").GET();
        if (!"NULL".equals(userId)) builder.header("X-User-Id", userId);
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

    @Test
    @DisplayName("삽입 순서와 무관하게 전체 경로를 순서대로 반환하고 실적도 매핑한다")
    void returnsAllRoutesInSequenceOrder() throws Exception {
        UUID companyRouteId = deliveryRouteRepository.findAllByDeliveryIdOrderBySequenceAsc(deliveryId).getFirst().getId();
        jdbc.update("UPDATE p_delivery_routes SET sequence = 2 WHERE id = ?", companyRouteId);
        var delivery = deliveryRepository.findById(deliveryId).orElseThrow();
        UUID origin = UUID.randomUUID();
        UUID destination = delivery.getDestinationHubId();
        UUID hubRouteId = insertRoute(1, origin, destination, "HUB_TO_HUB");
        jdbc.update("UPDATE p_delivery_routes SET expected_distance = 123.45, expected_duration = 90 WHERE id = ?", hubRouteId);
        jdbc.update("UPDATE p_delivery_routes SET manager_id = ?, actual_distance = 125.50, actual_duration = 95 WHERE id = ?",
                userId, hubRouteId);

        // 본인 구간 하나뿐 아니라 다른 담당자의 구간까지 전체 목록을 조회한다.
        HttpResponse<String> response = get(deliveryId, userId.toString(), "reader", "USER");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat((java.util.List<Integer>) JsonPath.<java.util.List<Integer>>read(response.body(), "$[*].sequence"))
                .containsExactly(1, 2);
        assertThat((String) JsonPath.read(response.body(), "$[0].deliveryRouteId")).isEqualTo(hubRouteId.toString());
        assertThat((String) JsonPath.read(response.body(), "$[1].deliveryRouteId")).isEqualTo(companyRouteId.toString());
        assertThat((String) JsonPath.read(response.body(), "$[0].managerId")).isEqualTo(userId.toString());
        assertThat((String) JsonPath.read(response.body(), "$[0].departureHubId")).isEqualTo(origin.toString());
        assertThat((String) JsonPath.read(response.body(), "$[0].destinationHubId")).isEqualTo(destination.toString());
        assertThat(((Number) JsonPath.read(response.body(), "$[0].expectedDistance")).doubleValue()).isEqualTo(123.45);
        assertThat((Integer) JsonPath.read(response.body(), "$[0].expectedDuration")).isEqualTo(90);
        assertThat(((Number) JsonPath.read(response.body(), "$[0].actualDistance")).doubleValue()).isEqualTo(125.50);
        assertThat((Integer) JsonPath.read(response.body(), "$[0].actualDuration")).isEqualTo(95);
        assertThat((String) JsonPath.read(response.body(), "$[0].type")).isEqualTo("HUB_TO_HUB");
        verifyNoInteractions(orderClient);
    }

    @Test
    @DisplayName("논리 삭제된 경로는 반환 목록에서 제외한다")
    void excludesDeletedRoutes() throws Exception {
        jdbc.update("UPDATE p_delivery_routes SET deleted_at = CURRENT_TIMESTAMP, deleted_by = '00000000-0000-4000-8000-000000000001' WHERE delivery_id = ?", deliveryId);
        var delivery = deliveryRepository.findById(deliveryId).orElseThrow();
        UUID activeId = insertRoute(2, delivery.getDestinationHubId(), null, "HUB_TO_COMPANY");
        HttpResponse<String> response = get(deliveryId, userId.toString(), "reader", "MASTER");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat((Integer) JsonPath.read(response.body(), "$.length()")).isEqualTo(1);
        assertThat((String) JsonPath.read(response.body(), "$[0].deliveryRouteId")).isEqualTo(activeId.toString());
    }

    @Test
    @DisplayName("조회 가능한 배송에 활성 경로가 없으면 빈 배열을 반환한다")
    void returnsEmptyArrayWithoutActiveRoutes() throws Exception {
        jdbc.update("UPDATE p_delivery_routes SET deleted_at = CURRENT_TIMESTAMP, deleted_by = '00000000-0000-4000-8000-000000000001' WHERE delivery_id = ?", deliveryId);
        HttpResponse<String> response = get(deliveryId, userId.toString(), "reader", "MASTER");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat((Integer) JsonPath.read(response.body(), "$.length()")).isZero();
    }

    // 조회용 추가 경로는 감사 필드까지 명시해 테스트 DB에 준비한다.
    private UUID insertRoute(int sequence, UUID departure, UUID destination, String type) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO p_delivery_routes
                (id, delivery_id, sequence, departure_hub_id, destination_hub_id,
                 status, type, created_at, created_by, updated_at, updated_by)
                VALUES (?, ?, ?, ?, ?, 'WAITING', ?, CURRENT_TIMESTAMP, '00000000-0000-4000-8000-000000000001', CURRENT_TIMESTAMP, '00000000-0000-4000-8000-000000000001')
                """, id, deliveryId, sequence, departure, destination, type);
        return id;
    }

    private void assertDetail(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat((Integer) JsonPath.read(response.body(), "$.length()")).isEqualTo(1);
        assertThat((String) JsonPath.read(response.body(), "$[0].deliveryRouteId")).isNotBlank();
        assertThat((Integer) JsonPath.read(response.body(), "$[0].sequence")).isEqualTo(1);
        assertThat((String) JsonPath.read(response.body(), "$[0].type")).isEqualTo("HUB_TO_COMPANY");
        assertThat((String) JsonPath.read(response.body(), "$[0].status")).isEqualTo("WAITING");
        assertThat((Object) JsonPath.read(response.body(), "$[0].destinationHubId")).isNull();
        assertThat((Object) JsonPath.read(response.body(), "$[0].expectedDistance")).isNull();
        assertThat((Object) JsonPath.read(response.body(), "$[0].expectedDuration")).isNull();
    }
}

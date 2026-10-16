package logistics.delivery;

import com.jayway.jsonpath.JsonPath;
import logistics.delivery.domain.enums.DeliveryStatus;
import logistics.delivery.infrastructure.client.HubClient;
import logistics.delivery.infrastructure.client.dto.HubRouteResponse;
import logistics.delivery.infrastructure.repository.DeliveryRepository;
import logistics.delivery.infrastructure.repository.DeliveryRouteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:delivery-cancellation-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "eureka.client.enabled=false",
                "eureka.client.service-url.defaultZone=http://localhost:8761/eureka/",
                "spring.cloud.discovery.enabled=false"
        }
)
class DeliveryCancellationIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private DeliveryRouteRepository deliveryRouteRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private HubClient hubClient;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @BeforeEach
    void cleanTestDatabase() {
        // 테스트 전용 H2만 사용한다. 로컬 PostgreSQL 데이터는 변경하지 않는다.
        deliveryRouteRepository.deleteAllInBatch();
        deliveryRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("대기 배송 취소는 200을 반환하고 배송·모든 경로·수정 감사값을 함께 변경한다")
    void cancelsDeliveryAndEveryRoute() throws Exception {
        UUID deliveryId = createDeliveryWithTwoRoutes();
        DatabaseSnapshot before = snapshot(deliveryId);

        HttpResponse<String> response = cancel(deliveryId, "canceluser");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat((String) JsonPath.read(response.body(), "$.deliveryId"))
                .isEqualTo(deliveryId.toString());
        assertThat((String) JsonPath.read(response.body(), "$.status"))
                .isEqualTo("CANCELLED");
        DatabaseSnapshot after = snapshot(deliveryId);
        assertCancelledAudit(before.delivery(), after.delivery());
        assertThat(after.routes()).hasSize(2);
        for (int i = 0; i < after.routes().size(); i++) {
            assertCancelledAudit(before.routes().get(i), after.routes().get(i));
        }
        assertThat(deliveryRepository.count()).isEqualTo(1);
        assertThat(deliveryRouteRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("반복 취소는 200을 반환하고 최초 취소 상태·감사 기록을 유지한다")
    void repeatedCancellationPreservesFirstAudit() throws Exception {
        UUID deliveryId = createDeliveryWithTwoRoutes();
        assertThat(cancel(deliveryId, "canceluser").statusCode()).isEqualTo(200);
        DatabaseSnapshot firstCancellation = snapshot(deliveryId);

        HttpResponse<String> response = cancel(deliveryId, "retryuser");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat((String) JsonPath.read(response.body(), "$.deliveryId"))
                .isEqualTo(deliveryId.toString());
        assertThat((String) JsonPath.read(response.body(), "$.status"))
                .isEqualTo("CANCELLED");
        assertThat(snapshot(deliveryId)).isEqualTo(firstCancellation);
    }

    @Test
    @DisplayName("존재하지 않는 배송 취소는 404를 반환하고 다른 배송을 변경하지 않는다")
    void missingDeliveryReturns404() throws Exception {
        UUID existingId = createDeliveryWithTwoRoutes();
        DatabaseSnapshot before = snapshot(existingId);

        HttpResponse<String> response = cancel(UUID.randomUUID(), "canceluser");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(snapshot(existingId)).isEqualTo(before);
        assertThat(deliveryRepository.count()).isEqualTo(1);
        assertThat(deliveryRouteRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("논리 삭제된 배송 취소는 404를 반환하고 삭제 이력을 유지한다")
    void deletedDeliveryReturns404() throws Exception {
        UUID deliveryId = createDeliveryWithTwoRoutes();
        assertThat(cancel(deliveryId, "canceluser").statusCode()).isEqualTo(200);
        // 삭제 API가 아직 없으므로 테스트 DB에만 논리 삭제 상태를 준비한다.
        jdbc.update("UPDATE p_delivery_routes SET deleted_at = CURRENT_TIMESTAMP, deleted_by = ? WHERE delivery_id = ?",
                "deleteuser", deliveryId);
        jdbc.update("UPDATE p_deliveries SET deleted_at = CURRENT_TIMESTAMP, deleted_by = ? WHERE id = ?",
                "deleteuser", deliveryId);
        DatabaseSnapshot before = snapshot(deliveryId);

        HttpResponse<String> response = cancel(deliveryId, "retryuser");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(snapshot(deliveryId)).isEqualTo(before);
    }

    @ParameterizedTest(name = "{0} 상태에서는 취소 불가")
    @EnumSource(value = DeliveryStatus.class, names = {
            "HUB_IN_TRANSIT", "HUB_ARRIVED", "COMPANY_IN_TRANSIT", "DELIVERED"
    })
    @DisplayName("출발 이후 모든 배송 상태는 409를 반환하고 기존 데이터를 유지한다")
    void rejectsDeliveryAfterDeparture(DeliveryStatus status) throws Exception {
        UUID deliveryId = createDeliveryWithTwoRoutes();
        // 상태 변경 API가 아직 없으므로 테스트 DB에서만 출발 이후 상태를 준비한다.
        jdbc.update("UPDATE p_deliveries SET status = ? WHERE id = ?",
                status.name(), deliveryId);
        DatabaseSnapshot before = snapshot(deliveryId);

        HttpResponse<String> response = cancel(deliveryId, "canceluser");

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(snapshot(deliveryId)).isEqualTo(before);
    }

    @Test
    @DisplayName("뒤 경로의 취소 실패는 409를 반환하고 먼저 변경한 배송·앞 경로까지 롤백한다")
    void rollsBackDeliveryAndEarlierRouteWhenLaterRouteCannotCancel() throws Exception {
        UUID deliveryId = createDeliveryWithTwoRoutes();
        // 두 번째 경로에서 실패시켜 부모뿐 아니라 첫 번째 경로의 변경도 검증한다.
        jdbc.update("UPDATE p_delivery_routes SET status = 'IN_TRANSIT' WHERE delivery_id = ? AND sequence = 2",
                deliveryId);
        DatabaseSnapshot before = snapshot(deliveryId);

        HttpResponse<String> response = cancel(deliveryId, "canceluser");

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(snapshot(deliveryId)).isEqualTo(before);
        assertThat(snapshot(deliveryId).delivery().get("STATUS")).isEqualTo("WAITING");
        assertThat(snapshot(deliveryId).routes().getFirst().get("STATUS")).isEqualTo("WAITING");
    }

    private UUID createDeliveryWithTwoRoutes() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID originId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        // 허브 통신은 대체하고 취소 대상인 두 경로를 실제 생성 API로 저장한다.
        when(hubClient.getRoute(originId, destinationId)).thenReturn(new HubRouteResponse(
                destinationId, "도착 허브", new BigDecimal("100.5"), 90,
                UUID.randomUUID(), originId, "출발 허브"
        ));
        String body = """
                {
                  "orderId": "%s",
                  "departureHubId": "%s",
                  "destinationHubId": "%s",
                  "address": "서울 테스트 주소",
                  "recipientName": "테스트 수령인",
                  "recipientPhone": "010-1234-5678"
                }
                """.formatted(orderId, originId, destinationId);
        HttpResponse<String> response = send("/internal/deliveries", "createuser", body);
        assertThat(response.statusCode()).isEqualTo(201);
        String id = JsonPath.read(response.body(), "$.deliveryId");
        UUID deliveryId = UUID.fromString(id);
        assertThat(snapshot(deliveryId).routes()).hasSize(2);
        return deliveryId;
    }

    private HttpResponse<String> cancel(UUID deliveryId, String username) throws Exception {
        return send("/internal/deliveries/" + deliveryId + "/cancel", username, null);
    }

    private HttpResponse<String> send(String path, String username, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .header("X-User-Name", username)
                .header("X-Role-Name", "USER");
        if (body == null) {
            builder.POST(HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        return httpClient.send(builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private DatabaseSnapshot snapshot(UUID deliveryId) {
        return new DatabaseSnapshot(
                jdbc.queryForMap("SELECT * FROM p_deliveries WHERE id = ?", deliveryId),
                jdbc.queryForList("SELECT * FROM p_delivery_routes WHERE delivery_id = ? ORDER BY sequence",
                        deliveryId)
        );
    }

    private void assertCancelledAudit(Map<String, Object> before, Map<String, Object> after) {
        assertThat(after.get("STATUS")).isEqualTo("CANCELLED");
        assertThat(after.get("CREATED_BY")).isEqualTo(before.get("CREATED_BY"));
        assertThat(after.get("CREATED_AT")).isEqualTo(before.get("CREATED_AT"));
        assertThat(after.get("UPDATED_BY")).isEqualTo("canceluser");
        assertThat(after.get("UPDATED_AT")).isNotNull();
        assertThat(after.get("DELETED_AT")).isNull();
        assertThat(after.get("DELETED_BY")).isNull();
    }

    private record DatabaseSnapshot(
            Map<String, Object> delivery,
            List<Map<String, Object>> routes
    ) {
    }
}

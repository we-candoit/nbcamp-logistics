package logistics.delivery;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManagerFactory;
import logistics.delivery.domain.entity.Delivery;
import logistics.delivery.domain.entity.DeliveryRoute;
import logistics.delivery.domain.enums.DeliveryStatus;
import logistics.delivery.infrastructure.client.HubClient;
import logistics.delivery.infrastructure.client.dto.HubRouteResponse;
import logistics.delivery.infrastructure.repository.DeliveryRepository;
import logistics.delivery.infrastructure.repository.DeliveryRouteRepository;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
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
                "spring.datasource.url=jdbc:h2:mem:delivery-deletion-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                // 롤백 테스트에서 실패 전에 실제로 UPDATE가 실행됐는지 확인하기 위해 사용한다.
                "spring.jpa.properties.hibernate.generate_statistics=true",
                "eureka.client.enabled=false",
                "eureka.client.service-url.defaultZone=http://localhost:8761/eureka/",
                "spring.cloud.discovery.enabled=false"
        }
)
class DeliveryDeletionIntegrationTest {

    private static final String FAIL_ROUTE_DELETE_CONSTRAINT = "chk_test_fail_route2_delete";

    @LocalServerPort
    private int port;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private DeliveryRouteRepository deliveryRouteRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @MockitoBean
    private HubClient hubClient;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @BeforeEach
    void cleanTestDatabase() {
        // 테스트 전용 H2만 사용한다. 로컬 PostgreSQL 데이터는 변경하지 않는다.
        dropFailureInjection();
        deliveryRouteRepository.deleteAllInBatch();
        deliveryRepository.deleteAllInBatch();
    }

    @AfterEach
    void removeFailureInjection() {
        // 같은 H2를 쓰는 다른 테스트에 실패 주입이 남지 않게 한다.
        dropFailureInjection();
    }

    @ParameterizedTest(name = "{0} 역할 요청")
    @ValueSource(strings = {"USER", "MASTER"})
    @DisplayName("취소된 배송 삭제는 204를 반환하고 배송·모든 경로를 같은 시각·삭제자로 논리 삭제한다")
    void softDeletesCancelledDeliveryAndEveryRoute(String role) throws Exception {
        UUID deliveryId = createCancelledDeliveryWithTwoRoutes();
        DatabaseSnapshot before = snapshot(deliveryId);

        HttpResponse<String> response = delete(deliveryId, "deleteuser", role);

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(response.body()).isEmpty();
        DatabaseSnapshot after = snapshot(deliveryId);
        // 실제 행은 삭제하지 않는다.
        assertThat(deliveryRepository.count()).isEqualTo(1);
        assertThat(deliveryRouteRepository.count()).isEqualTo(2);
        assertThat(after.routes()).hasSize(2);

        Object deletedAt = after.delivery().get("DELETED_AT");
        assertThat(deletedAt).isNotNull();
        assertSoftDeleted(before.delivery(), after.delivery(), deletedAt, "deleteuser");
        for (int i = 0; i < after.routes().size(); i++) {
            assertSoftDeleted(before.routes().get(i), after.routes().get(i), deletedAt, "deleteuser");
        }
    }

    @Test
    @DisplayName("반복 삭제는 다른 사용자가 요청해도 204를 반환하고 최초 삭제·수정 감사 기록을 유지한다")
    void repeatedDeletionPreservesFirstDeletionAudit() throws Exception {
        UUID deliveryId = createCancelledDeliveryWithTwoRoutes();
        assertThat(delete(deliveryId, "deleteuser", "USER").statusCode()).isEqualTo(204);
        DatabaseSnapshot firstDeletion = snapshot(deliveryId);
        assertThat(firstDeletion.delivery().get("DELETED_AT")).isNotNull();

        HttpResponse<String> response = delete(deliveryId, "retryuser", "MASTER");

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(response.body()).isEmpty();
        // 삭제 시각·삭제자뿐 아니라 updatedAt·updatedBy까지 전체 행이 그대로여야 한다.
        assertThat(snapshot(deliveryId)).isEqualTo(firstDeletion);
        assertThat(deliveryRepository.count()).isEqualTo(1);
        assertThat(deliveryRouteRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("존재하지 않는 배송 삭제는 404를 반환하고 다른 배송을 변경하지 않는다")
    void missingDeliveryReturns404() throws Exception {
        // 삭제 가능한 배송이 있어도 다른 ID 요청이 그 배송을 건드리지 않아야 한다.
        UUID existingId = createCancelledDeliveryWithTwoRoutes();
        DatabaseSnapshot before = snapshot(existingId);

        HttpResponse<String> response = delete(UUID.randomUUID(), "deleteuser", "USER");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(snapshot(existingId)).isEqualTo(before);
        assertThat(snapshot(existingId).delivery().get("DELETED_AT")).isNull();
        assertThat(deliveryRepository.count()).isEqualTo(1);
        assertThat(deliveryRouteRepository.count()).isEqualTo(2);
    }

    @ParameterizedTest(name = "{0} 상태에서는 삭제 불가")
    @EnumSource(value = DeliveryStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "CANCELLED")
    @DisplayName("취소되지 않은 배송은 409를 반환하고 배송·경로 데이터를 변경하지 않는다")
    void rejectsDeliveryThatIsNotCancelled(DeliveryStatus status) throws Exception {
        UUID deliveryId = createDeliveryWithTwoRoutes();
        // 상태 변경 API가 아직 없으므로 테스트 DB에서만 대상 상태를 준비한다.
        jdbc.update("UPDATE p_deliveries SET status = ? WHERE id = ?", status.name(), deliveryId);
        DatabaseSnapshot before = snapshot(deliveryId);
        assertThat(before.delivery().get("STATUS")).isEqualTo(status.name());

        HttpResponse<String> response = delete(deliveryId, "deleteuser", "USER");

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(snapshot(deliveryId)).isEqualTo(before);
        assertThat(snapshot(deliveryId).delivery().get("DELETED_AT")).isNull();
        assertThat(snapshot(deliveryId).delivery().get("DELETED_BY")).isNull();
        assertThat(snapshot(deliveryId).routes())
                .allSatisfy(route -> {
                    assertThat(route.get("DELETED_AT")).isNull();
                    assertThat(route.get("DELETED_BY")).isNull();
                });
        assertThat(deliveryRepository.count()).isEqualTo(1);
        assertThat(deliveryRouteRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("뒤 경로 저장 실패는 5xx를 반환하고 먼저 변경한 배송·앞 경로의 삭제·감사 기록까지 롤백한다")
    void rollsBackDeliveryAndEarlierRouteWhenLaterRouteCannotBeSaved() throws Exception {
        UUID deliveryId = createCancelledDeliveryWithTwoRoutes();
        // 테스트 H2에만 제약을 추가해 두 번째 경로의 논리 삭제 UPDATE를 DB가 거절하게 한다.
        // 애플리케이션 코드나 엔티티는 바꾸지 않는다.
        jdbc.execute("ALTER TABLE p_delivery_routes ADD CONSTRAINT " + FAIL_ROUTE_DELETE_CONSTRAINT
                + " CHECK (NOT (sequence = 2 AND deleted_at IS NOT NULL))");
        DatabaseSnapshot before = snapshot(deliveryId);
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        HttpResponse<String> response = delete(deliveryId, "deleteuser", "USER");

        assertThat(response.statusCode()).isEqualTo(500);
        // 관리 기록 UPDATE와 구분해 배송과 배송 경로의 1구간이 실제로 변경됐는지 확인한다.
        assertThat(statistics.getEntityStatistics(Delivery.class.getName()).getUpdateCount()).isEqualTo(1);
        assertThat(statistics.getEntityStatistics(DeliveryRoute.class.getName()).getUpdateCount()).isEqualTo(1);
        // 응답 후 DB를 다시 조회해 배송·모든 경로가 삭제 전 상태 그대로인지 확인한다.
        DatabaseSnapshot after = snapshot(deliveryId);
        assertThat(after).isEqualTo(before);
        assertThat(after.delivery().get("DELETED_AT")).isNull();
        assertThat(after.delivery().get("DELETED_BY")).isNull();
        assertThat(after.delivery().get("STATUS")).isEqualTo("CANCELLED");
        assertThat(after.routes()).hasSize(2).allSatisfy(route -> {
            assertThat(route.get("DELETED_AT")).isNull();
            assertThat(route.get("DELETED_BY")).isNull();
            assertThat(route.get("STATUS")).isEqualTo("CANCELLED");
        });
    }

    private void dropFailureInjection() {
        jdbc.execute("ALTER TABLE p_delivery_routes DROP CONSTRAINT IF EXISTS "
                + FAIL_ROUTE_DELETE_CONSTRAINT);
    }

    private UUID createCancelledDeliveryWithTwoRoutes() throws Exception {
        UUID deliveryId = createDeliveryWithTwoRoutes();
        // 취소 API로 배송과 모든 경로를 CANCELLED 상태로 만든다.
        HttpResponse<String> cancelResponse = send("POST", "/internal/deliveries/" + deliveryId + "/cancel",
                "canceluser", "USER", null);
        assertThat(cancelResponse.statusCode()).isEqualTo(200);
        DatabaseSnapshot snapshot = snapshot(deliveryId);
        assertThat(snapshot.delivery().get("STATUS")).isEqualTo("CANCELLED");
        assertThat(snapshot.routes()).hasSize(2)
                .allSatisfy(route -> assertThat(route.get("STATUS")).isEqualTo("CANCELLED"));
        return deliveryId;
    }

    private UUID createDeliveryWithTwoRoutes() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID originId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        // 허브 통신은 대체하고 삭제 대상인 두 경로를 실제 생성 API로 저장한다.
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
        HttpResponse<String> response = send("POST", "/internal/deliveries", "createuser", "USER", body);
        assertThat(response.statusCode()).isEqualTo(201);
        String id = JsonPath.read(response.body(), "$.deliveryId");
        UUID deliveryId = UUID.fromString(id);
        assertThat(snapshot(deliveryId).routes()).hasSize(2);
        return deliveryId;
    }

    private HttpResponse<String> delete(UUID deliveryId, String username, String role) throws Exception {
        return send("DELETE", "/internal/deliveries/" + deliveryId, username, role, null);
    }

    private HttpResponse<String> send(String method, String path, String username, String role, String body)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .header("X-User-Id", UUID.nameUUIDFromBytes(username.getBytes(StandardCharsets.UTF_8)).toString())
                .header("X-Role-Name", role);
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
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

    private void assertSoftDeleted(Map<String, Object> before, Map<String, Object> after,
                                   Object expectedDeletedAt, String expectedDeletedBy) {
        assertThat(after.get("STATUS")).isEqualTo("CANCELLED");
        assertThat(after.get("DELETED_AT")).isEqualTo(expectedDeletedAt);
        assertThat(after.get("DELETED_BY")).isEqualTo(UUID.nameUUIDFromBytes(expectedDeletedBy.getBytes(StandardCharsets.UTF_8)));
        assertThat(after.get("ID")).isEqualTo(before.get("ID"));
        assertThat(after.get("CREATED_BY")).isEqualTo(before.get("CREATED_BY"));
        assertThat(after.get("CREATED_AT")).isEqualTo(before.get("CREATED_AT"));
    }

    private record DatabaseSnapshot(
            Map<String, Object> delivery,
            List<Map<String, Object>> routes
    ) {
    }
}

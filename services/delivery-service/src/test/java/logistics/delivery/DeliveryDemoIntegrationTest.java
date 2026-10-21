package logistics.delivery;

import com.jayway.jsonpath.JsonPath;
import logistics.delivery.config.DeliveryDemoProperties;
import logistics.delivery.infrastructure.client.HubClient;
import logistics.delivery.infrastructure.client.UserClient;
import logistics.delivery.infrastructure.client.dto.HubRouteResponse;
import logistics.delivery.infrastructure.client.dto.UserDetailResponse;
import logistics.delivery.infrastructure.client.dto.UserApiResponse;
import logistics.delivery.infrastructure.repository.DeliveryOrderControlRepository;
import logistics.delivery.infrastructure.repository.DeliveryRepository;
import logistics.delivery.infrastructure.repository.DeliveryRouteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** demo 설정을 바인딩하고 실제 HTTP와 전용 H2로 검증한다. 외부 허브, 사용자 응답은 대체한다. */
@ActiveProfiles({"test", "demo"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:delivery-demo-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop",
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
        "eureka.client.service-url.defaultZone=http://localhost:8761/eureka/",
        "delivery.demo.auto-assignment.enabled=true",
        "delivery.demo.auto-assignment.hub-managers[22222222-2222-4222-8222-222222222222]=88888888-8888-4888-8888-888888888888",
        "delivery.demo.auto-assignment.hub-managers[33333333-3333-4333-8333-333333333333]=77777777-7777-4777-8777-777777777777"
})
class DeliveryDemoIntegrationTest {

    private static final UUID HUB_A = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID HUB_B = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID MANAGER_A = UUID.fromString("88888888-8888-4888-8888-888888888888");
    private static final UUID MANAGER_B = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final UUID MASTER = UUID.fromString("99999999-9999-4999-8999-999999999999");

    @LocalServerPort
    private int port;
    @Autowired
    private DeliveryDemoProperties properties;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DeliveryRepository deliveries;
    @Autowired
    private DeliveryRouteRepository routes;
    @Autowired
    private DeliveryOrderControlRepository controls;
    @MockitoBean
    private HubClient hubClient;
    @MockitoBean
    private UserClient userClient;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    /** 로컬 DB 대신 테스트 H2만 비우고 각 테스트에서 변경한 시연 스위치를 복구한다. */
    @BeforeEach
    void prepareDatabase() {
        routes.deleteAllInBatch();
        deliveries.deleteAllInBatch();
        controls.deleteAllInBatch();
        properties.getAutoAssignment().setEnabled(true);
        properties.getActualMetrics().setEnabled(true);
    }

    /** 설정 파일의 UUID 매핑이 바인딩되고 모든 배송 경로에 출발 허브 담당자가 적용되는지 확인한다. */
    @Test
    @DisplayName("자동 배정은 출발 허브별 담당자를 사용하고 업체 담당자를 배송 전체에도 반영한다")
    void assignsManagersByDepartureHub() throws Exception {
        assertThat(properties.getAutoAssignment().getHubManagers())
                .containsEntry(HUB_A, MANAGER_A).containsEntry(HUB_B, MANAGER_B);
        Fixture fixture = create(HUB_A, HUB_B);
        List<Map<String, Object>> savedRoutes = routeRows(fixture);

        assertThat(savedRoutes).hasSize(2);
        assertThat(savedRoutes.get(0).get("MANAGER_ID")).isEqualTo(MANAGER_A);
        assertThat(savedRoutes.get(1).get("MANAGER_ID")).isEqualTo(MANAGER_B);
        assertThat(deliveryRow(fixture).get("MANAGER_ID")).isEqualTo(MANAGER_B);
        assertThat(savedRoutes).allSatisfy(route -> {
            assertThat(route.get("STATUS")).isEqualTo("WAITING");
            assertThat(route.get("ACTUAL_DISTANCE")).isNull();
            assertThat(route.get("ACTUAL_DURATION")).isNull();
        });
        assertThat((BigDecimal) savedRoutes.get(0).get("EXPECTED_DISTANCE")).isEqualByComparingTo("42.50");
        assertThat(savedRoutes.get(0).get("EXPECTED_DURATION")).isEqualTo(60);
        verifyNoInteractions(userClient);
    }

    /** 같은 허브 배송도 업체 배송 경로 하나와 배송 전체에 담당자를 배정한다. */
    @Test
    @DisplayName("같은 허브 배송의 유일한 업체 배송 경로에도 자동 배정한다")
    void assignsSingleCompanyRoute() throws Exception {
        Fixture fixture = create(HUB_A, HUB_A);
        assertThat(routeRows(fixture)).hasSize(1);
        assertThat(routeRows(fixture).getFirst().get("MANAGER_ID")).isEqualTo(MANAGER_A);
        assertThat(deliveryRow(fixture).get("MANAGER_ID")).isEqualTo(MANAGER_A);
        verifyNoInteractions(hubClient, userClient);
    }

    /** 매핑이 없는 허브는 배송 생성을 막지 않고 미배정 상태로 남긴다. */
    @Test
    @DisplayName("담당자 매핑이 없는 허브는 미배정으로 생성되고 MASTER가 수동 배정할 수 있다")
    void unmappedHubAllowsManualAssignment() throws Exception {
        UUID unknownHub = UUID.randomUUID();
        Fixture fixture = create(HUB_A, unknownHub);
        assertThat(routeRows(fixture).getFirst().get("MANAGER_ID")).isEqualTo(MANAGER_A);
        assertThat(routeRows(fixture).getLast().get("MANAGER_ID")).isNull();
        assertThat(deliveryRow(fixture).get("MANAGER_ID")).isNull();

        UUID manager = UUID.randomUUID();
        mockEligibleUser(manager, unknownHub);
        assertThat(assign(fixture, 1, manager, MASTER, "MASTER").statusCode()).isEqualTo(200);
        assertThat(routeRows(fixture).getLast().get("MANAGER_ID")).isEqualTo(manager);
        assertThat(deliveryRow(fixture).get("MANAGER_ID")).isEqualTo(manager);
    }

    /** 자동 배정을 꺼도 MASTER 수동 배정과 고정 실적 기록은 독립적으로 동작한다. */
    @Test
    @DisplayName("자동 배정 OFF에서도 수동 배정 후 도착 시 고정 거리와 시간을 기록한다")
    void manualAssignmentAndMetricsWorkWhenAutoAssignmentDisabled() throws Exception {
        properties.getAutoAssignment().setEnabled(false);
        Fixture fixture = create(HUB_A, HUB_A);
        assertThat(routeRows(fixture).getFirst().get("MANAGER_ID")).isNull();
        assertThat(deliveryRow(fixture).get("MANAGER_ID")).isNull();
        verifyNoInteractions(userClient);

        UUID manager = UUID.randomUUID();
        assertError(assign(fixture, 0, manager, manager, "USER"), 403, "MANAGER_ASSIGNMENT_FORBIDDEN");
        mockEligibleUser(manager, HUB_A);
        assertThat(assign(fixture, 0, manager, MASTER, "MASTER").statusCode()).isEqualTo(200);
        assertThat(status(fixture, 0, "IN_TRANSIT", manager, "USER").statusCode()).isEqualTo(200);
        assertThat(status(fixture, 0, "ARRIVED", manager, "USER").statusCode()).isEqualTo(200);
        assertMetrics(routeRows(fixture).getFirst(), "5.00", 15);
    }

    /** 생성 재요청이나 스위치 변경이 이미 저장된 담당자와 감사 기록을 덮어쓰지 않는지 확인한다. */
    @Test
    @DisplayName("생성 재요청은 기존 담당자를 유지하며 자동 배정을 켜도 과거 배송을 소급 배정하지 않는다")
    void creationRetriesDoNotReassign() throws Exception {
        Fixture assigned = create(HUB_A, HUB_B);
        List<Map<String, Object>> routesBefore = routeRows(assigned);
        Map<String, Object> deliveryBefore = deliveryRow(assigned);
        properties.getAutoAssignment().setEnabled(false);
        assertThat(send("POST", "/internal/deliveries", MASTER, "MASTER", assigned.body()).statusCode()).isEqualTo(200);
        assertThat(routeRows(assigned)).isEqualTo(routesBefore);
        assertThat(deliveryRow(assigned)).isEqualTo(deliveryBefore);

        Fixture unassigned = create(HUB_A, HUB_A);
        List<Map<String, Object>> unassignedBefore = routeRows(unassigned);
        properties.getAutoAssignment().setEnabled(true);
        assertThat(send("POST", "/internal/deliveries", MASTER, "MASTER", unassigned.body()).statusCode()).isEqualTo(200);
        assertThat(routeRows(unassigned)).isEqualTo(unassignedBefore);
        assertThat(deliveryRow(unassigned).get("MANAGER_ID")).isNull();
    }

    /** 자동 배정된 담당자의 변경도 기존 수동 배정 정책에 따라 거절한다. */
    @Test
    @DisplayName("자동 배정된 배송 경로의 담당자 변경은 기존 정책대로 409이다")
    void cannotReplaceAutomaticallyAssignedManager() throws Exception {
        Fixture fixture = create(HUB_A, HUB_A);
        UUID replacement = UUID.randomUUID();
        mockEligibleUser(replacement, HUB_A);
        Map<String, Object> before = routeRows(fixture).getFirst();
        assertError(assign(fixture, 0, replacement, MASTER, "MASTER"), 409, "MANAGER_ALREADY_ASSIGNED");
        assertThat(routeRows(fixture).getFirst()).isEqualTo(before);
    }

    /** 기존 권한, 배송 경로 순서, 상태 전이와 도착 재요청 정책을 함께 검증한다. */
    @Test
    @DisplayName("최초 도착에만 유형별 고정 실적을 기록하고 앞선 배송 경로 재요청은 전체 상태를 되돌리지 않는다")
    void recordsMetricsOnceWhilePreservingStatusRules() throws Exception {
        Fixture fixture = create(HUB_A, HUB_B);
        assertError(status(fixture, 0, "IN_TRANSIT", MANAGER_B, "USER"), 403, "ROUTE_STATUS_UPDATE_FORBIDDEN");
        assertError(status(fixture, 1, "IN_TRANSIT", MANAGER_B, "USER"), 409, "PREVIOUS_ROUTES_NOT_ARRIVED");
        assertError(status(fixture, 0, "ARRIVED", MANAGER_A, "USER"), 409, "INVALID_ROUTE_STATUS_TRANSITION");
        assertThat(status(fixture, 0, "IN_TRANSIT", MANAGER_A, "USER").statusCode()).isEqualTo(200);
        assertThat(routeRows(fixture).getFirst().get("ACTUAL_DISTANCE")).isNull();
        assertThat(status(fixture, 0, "ARRIVED", MANAGER_A, "USER").statusCode()).isEqualTo(200);
        assertMetrics(routeRows(fixture).getFirst(), "100.00", 90);
        assertThat(routeRows(fixture).getLast().get("ACTUAL_DISTANCE")).isNull();
        assertThat(status(fixture, 1, "IN_TRANSIT", MANAGER_B, "USER").statusCode()).isEqualTo(200);
        assertThat(status(fixture, 1, "ARRIVED", MANAGER_B, "USER").statusCode()).isEqualTo(200);
        assertMetrics(routeRows(fixture).getLast(), "5.00", 15);

        List<Map<String, Object>> beforeRetry = routeRows(fixture);
        Map<String, Object> deliveryBeforeRetry = deliveryRow(fixture);
        assertThat(status(fixture, 0, "ARRIVED", MANAGER_A, "USER").statusCode()).isEqualTo(200);
        assertThat(status(fixture, 1, "ARRIVED", MANAGER_B, "USER").statusCode()).isEqualTo(200);
        assertThat(routeRows(fixture)).isEqualTo(beforeRetry);
        assertThat(deliveryRow(fixture)).isEqualTo(deliveryBeforeRetry);
        assertThat(deliveryRow(fixture).get("STATUS")).isEqualTo("DELIVERED");
    }

    /** 실적 스위치를 꺼도 담당자 자동 배정은 그대로 사용할 수 있다. */
    @Test
    @DisplayName("고정 실적 기록 OFF에서도 자동 배정되며 도착 후 실적은 null을 유지한다")
    void metricsCanBeDisabledIndependently() throws Exception {
        properties.getActualMetrics().setEnabled(false);
        Fixture fixture = create(HUB_A, HUB_A);
        assertThat(status(fixture, 0, "IN_TRANSIT", MANAGER_A, "USER").statusCode()).isEqualTo(200);
        assertThat(status(fixture, 0, "ARRIVED", MANAGER_A, "USER").statusCode()).isEqualTo(200);
        Map<String, Object> arrived = routeRows(fixture).getFirst();
        assertThat(arrived.get("ACTUAL_DISTANCE")).isNull();
        assertThat(arrived.get("ACTUAL_DURATION")).isNull();
        // 켠 뒤 과거 ARRIVED를 재요청해도 최초 도착이 아니므로 소급 기록하지 않는다.
        properties.getActualMetrics().setEnabled(true);
        assertThat(status(fixture, 0, "ARRIVED", MANAGER_A, "USER").statusCode()).isEqualTo(200);
        assertThat(routeRows(fixture).getFirst()).isEqualTo(arrived);
    }

    /** 기존 실적이 있으면 최초 도착 처리에서도 그 값을 보존한다. */
    @Test
    @DisplayName("이미 저장된 거리와 시간은 시연용 고정값으로 덮어쓰지 않는다")
    void preservesExistingActualMetrics() throws Exception {
        Fixture fixture = create(HUB_A, HUB_A);
        assertThat(status(fixture, 0, "IN_TRANSIT", MANAGER_A, "USER").statusCode()).isEqualTo(200);
        jdbc.update("UPDATE p_delivery_routes SET actual_distance = 1.23, actual_duration = 7 WHERE id = ?",
                fixture.routeIds().getFirst());
        assertThat(status(fixture, 0, "ARRIVED", MANAGER_A, "USER").statusCode()).isEqualTo(200);
        assertMetrics(routeRows(fixture).getFirst(), "1.23", 7);
    }

    /** 내부 생성 API로 배송을 만들고 외부 허브 응답만 가짜로 준비한다. */
    private Fixture create(UUID origin, UUID destination) throws Exception {
        if (!origin.equals(destination)) {
            when(hubClient.getRoute(origin, destination)).thenReturn(new HubRouteResponse(destination, "Destination",
                    new BigDecimal("42.50"), 60, UUID.randomUUID(), origin, "Origin"));
        }
        String body = """
                {"orderId":"%s","departureHubId":"%s","destinationHubId":"%s",
                 "address":"Demo test address","recipientName":"Demo recipient","recipientPhone":"010-1234-5678"}
                """.formatted(UUID.randomUUID(), origin, destination);
        HttpResponse<String> response = send("POST", "/internal/deliveries", MASTER, "MASTER", body);
        assertThat(response.statusCode()).isEqualTo(201);
        UUID id = UUID.fromString(JsonPath.read(response.body(), "$.deliveryId"));
        List<UUID> routeIds = jdbc.queryForList("SELECT id FROM p_delivery_routes WHERE delivery_id = ? ORDER BY sequence",
                UUID.class, id);
        return new Fixture(id, routeIds, body);
    }

    /** 담당 자격과 허브 소속이 확인된 사용자 API 응답을 대체한다. */
    private void mockEligibleUser(UUID manager, UUID hub) {
        when(userClient.getUserDetail(manager, MASTER, "MASTER"))
                .thenReturn(new UserApiResponse<>(true, null,
                        new UserDetailResponse(manager, true, "HUB", "CONFIRMED", hub)));
    }

    /** 기존 수동 배정 API에 요청한다. */
    private HttpResponse<String> assign(Fixture fixture, int index, UUID manager, UUID requester, String role)
            throws Exception {
        return send("PUT", "/api/deliveries/" + fixture.id() + "/routes/" + fixture.routeIds().get(index) + "/assignee",
                requester, role, "{\"managerId\":\"" + manager + "\"}");
    }

    /** 기존 배송 경로 상태 변경 API에 요청한다. */
    private HttpResponse<String> status(Fixture fixture, int index, String status, UUID requester, String role)
            throws Exception {
        return send("PATCH", "/api/deliveries/" + fixture.id() + "/routes/" + fixture.routeIds().get(index) + "/status",
                requester, role, "{\"status\":\"" + status + "\"}");
    }

    /** 헤더와 본문을 포함하여 실제 테스트 서버에 요청한다. */
    private HttpResponse<String> send(String method, String path, UUID requester, String role, String body)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10)).header("X-User-Id", requester.toString())
                .header("X-Role-Name", role).header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** 테스트 배송의 모든 배송 경로와 감사 필드를 순서대로 조회한다. */
    private List<Map<String, Object>> routeRows(Fixture fixture) {
        return jdbc.queryForList("SELECT * FROM p_delivery_routes WHERE delivery_id = ? ORDER BY sequence", fixture.id());
    }

    /** 테스트 배송과 감사 필드를 조회한다. */
    private Map<String, Object> deliveryRow(Fixture fixture) {
        return jdbc.queryForMap("SELECT * FROM p_deliveries WHERE id = ?", fixture.id());
    }

    /** 거리와 시간의 실제 저장값을 검증한다. */
    private void assertMetrics(Map<String, Object> row, String distance, int duration) {
        assertThat((BigDecimal) row.get("ACTUAL_DISTANCE")).isEqualByComparingTo(distance);
        assertThat(row.get("ACTUAL_DURATION")).isEqualTo(duration);
    }

    /** 기존 HTTP 상태와 오류 코드가 유지되는지 검증한다. */
    private void assertError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo(code);
    }

    private record Fixture(UUID id, List<UUID> routeIds, String body) { }
}

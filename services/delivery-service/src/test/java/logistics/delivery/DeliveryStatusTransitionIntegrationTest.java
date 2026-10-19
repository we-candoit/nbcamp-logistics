package logistics.delivery;

import com.jayway.jsonpath.JsonPath;
import logistics.delivery.infrastructure.client.HubClient;
import logistics.delivery.infrastructure.client.OrderClient;
import logistics.delivery.infrastructure.client.UserClient;
import org.junit.jupiter.api.AfterEach;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 실제 HTTP 요청·JPA 트랜잭션·별도 H2 DB로 배송 경로 상태 변경을 검증한다.
 * 검색·배정 수동 테스트에서 사용한 PostgreSQL 데이터는 변경하지 않는다.
 * 사용자·주문·허브 클라이언트는 가짜 객체로 대체하고 호출되지 않는지 확인한다.
 * 실제 Gateway 인증, 외부 서비스 연동, PostgreSQL 잠금·동시성은 검증하지 않는다.
 * 전체 배송 진행은 P2P 기준이다. 복수 허브 배송 경로 사례는 선행 순서 거부만 검증하며,
 * 여러 허브를 경유하는 배송 전체 상태 전이를 지원한다는 뜻은 아니다.
 */
@ActiveProfiles("test")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:delivery-status-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "eureka.client.enabled=false",
                "eureka.client.service-url.defaultZone=http://localhost:8761/eureka/",
                "spring.cloud.discovery.enabled=false"
        }
)
class DeliveryStatusTransitionIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private HubClient hubClient;

    @MockitoBean
    private OrderClient orderClient;

    @MockitoBean
    private UserClient userClient;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    private UUID deliveryId;
    private UUID hubRouteId;
    private UUID companyRouteId;
    private UUID hubManagerId;
    private UUID companyManagerId;
    private UUID masterId;
    private UUID originHubId;
    private UUID destinationHubId;

    @BeforeEach
    void prepareAssignedDelivery() {
        jdbc.update("DELETE FROM p_delivery_routes");
        jdbc.update("DELETE FROM p_deliveries");
        deliveryId = UUID.randomUUID();
        hubRouteId = UUID.randomUUID();
        companyRouteId = UUID.randomUUID();
        hubManagerId = UUID.randomUUID();
        companyManagerId = UUID.randomUUID();
        masterId = UUID.randomUUID();
        originHubId = UUID.randomUUID();
        destinationHubId = UUID.randomUUID();
        insertDelivery(deliveryId, originHubId, destinationHubId);
        insertRoute(hubRouteId, 1, originHubId, destinationHubId, "HUB_TO_HUB", hubManagerId);
        insertRoute(companyRouteId, 2, destinationHubId, null, "HUB_TO_COMPANY", companyManagerId);
    }

    @AfterEach
    void neverCallsOtherServices() {
        verifyNoInteractions(hubClient, orderClient, userClient);
    }

    @Test
    @DisplayName("각 배송 경로 담당자의 순차 진행에 따라 배송 전체 상태와 감사 기록을 갱신한다")
    void completesP2PDeliveryWithDifferentManagers() throws Exception {
        Map<String, Object> untouchedCompany = routeRow(companyRouteId);

        assertResponse(patch(hubRouteId, "IN_TRANSIT", hubManagerId, "hubmanager", "USER"),
                hubRouteId, "IN_TRANSIT", "HUB_IN_TRANSIT");
        assertUpdatedAudit(deliveryRow(), "hubmanager");
        assertUpdatedAudit(routeRow(hubRouteId), "hubmanager");
        assertThat(routeRow(companyRouteId)).isEqualTo(untouchedCompany);

        assertResponse(patch(hubRouteId, "ARRIVED", hubManagerId, "hubmanager", "USER"),
                hubRouteId, "ARRIVED", "HUB_ARRIVED");
        Map<String, Object> completedHub = routeRow(hubRouteId);

        assertResponse(patch(companyRouteId, "IN_TRANSIT", companyManagerId, "companymanager", "USER"),
                companyRouteId, "IN_TRANSIT", "COMPANY_IN_TRANSIT");
        assertUpdatedAudit(deliveryRow(), "companymanager");
        assertUpdatedAudit(routeRow(companyRouteId), "companymanager");
        assertThat(routeRow(hubRouteId)).isEqualTo(completedHub);

        assertResponse(patch(companyRouteId, "ARRIVED", companyManagerId, "companymanager", "USER"),
                companyRouteId, "ARRIVED", "DELIVERED");
        assertThat(deliveryRow().get("status")).isEqualTo("DELIVERED");
        assertThat(routeRow(hubRouteId).get("status")).isEqualTo("ARRIVED");
        assertThat(routeRow(companyRouteId).get("status")).isEqualTo("ARRIVED");
        assertThat(deliveryRow().get("manager_id")).isEqualTo(companyManagerId);
        assertThat(routeRow(hubRouteId).get("manager_id")).isEqualTo(hubManagerId);
    }

    @Test
    @DisplayName("같은 허브 배송은 배송 경로의 1구간인 업체 배송부터 시작한다")
    void completesSameHubDelivery() throws Exception {
        jdbc.update("DELETE FROM p_delivery_routes WHERE id = ?", hubRouteId);
        jdbc.update("UPDATE p_deliveries SET departure_hub_id = ? WHERE id = ?", destinationHubId, deliveryId);
        jdbc.update("UPDATE p_delivery_routes SET sequence = 1 WHERE id = ?", companyRouteId);

        assertResponse(patch(companyRouteId, "IN_TRANSIT", companyManagerId, "companymanager", "USER"),
                companyRouteId, "IN_TRANSIT", "COMPANY_IN_TRANSIT");
        assertResponse(patch(companyRouteId, "ARRIVED", companyManagerId, "companymanager", "USER"),
                companyRouteId, "ARRIVED", "DELIVERED");
        assertThat(deliveryRow().get("status")).isEqualTo("DELIVERED");
    }

    @Test
    @DisplayName("MASTER는 본인 담당 배송 경로가 아니어도 정상 순서로 상태를 변경한다")
    void masterCanUpdateAssignedRoute() throws Exception {
        assertResponse(master(hubRouteId, "IN_TRANSIT"), hubRouteId, "IN_TRANSIT", "HUB_IN_TRANSIT");
        assertResponse(master(hubRouteId, "ARRIVED"), hubRouteId, "ARRIVED", "HUB_ARRIVED");
        assertUpdatedAudit(deliveryRow(), "master");
        assertUpdatedAudit(routeRow(hubRouteId), "master");
    }

    @ParameterizedTest
    @CsvSource({
            "HUB_IN_TRANSIT,hub,IN_TRANSIT,WAITING,IN_TRANSIT",
            "HUB_ARRIVED,hub,ARRIVED,WAITING,ARRIVED",
            "COMPANY_IN_TRANSIT,hub,ARRIVED,IN_TRANSIT,ARRIVED",
            "COMPANY_IN_TRANSIT,company,ARRIVED,IN_TRANSIT,IN_TRANSIT",
            "DELIVERED,hub,ARRIVED,ARRIVED,ARRIVED",
            "DELIVERED,company,ARRIVED,ARRIVED,ARRIVED"
    })
    @DisplayName("동일 상태 재요청은 감사 기록을 유지하고 배송 전체 상태를 되돌리지 않는다")
    void identicalStateKeepsEveryDatabaseField(String deliveryStatus, String target,
                                              String hubStatus, String companyStatus, String requested) throws Exception {
        setStates(deliveryStatus, hubStatus, companyStatus);
        UUID routeId = "hub".equals(target) ? hubRouteId : companyRouteId;
        State before = snapshot();
        assertResponse(master(routeId, requested), routeId, requested, deliveryStatus);
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"companyManagerOnHub", "hubManagerOnCompany", "stranger"})
    @DisplayName("배송 전체 담당자·다른 배송 경로 담당자·제삼자는 해당 배송 경로를 변경할 수 없다")
    void onlySpecificRouteManagerCanUpdate(String scenario) throws Exception {
        UUID routeId = "hubManagerOnCompany".equals(scenario) ? companyRouteId : hubRouteId;
        UUID requester = switch (scenario) {
            case "companyManagerOnHub" -> companyManagerId;
            case "hubManagerOnCompany" -> hubManagerId;
            default -> UUID.randomUUID();
        };
        State before = snapshot();
        assertThat(patch(routeId, "IN_TRANSIT", requester, "other", "USER").statusCode()).isEqualTo(403);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("동일 상태 재요청에서도 해당 배송 경로 담당자 권한을 확인한다")
    void retryDoesNotBypassAuthorization() throws Exception {
        setStates("HUB_IN_TRANSIT", "IN_TRANSIT", "WAITING");
        State before = snapshot();
        assertThat(patch(hubRouteId, "IN_TRANSIT", companyManagerId, "other", "USER").statusCode()).isEqualTo(403);
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"hub", "company"})
    @DisplayName("MASTER도 담당자가 없는 배송 경로를 출발시킬 수 없다")
    void cannotStartUnassignedRoute(String target) throws Exception {
        UUID routeId = "hub".equals(target) ? hubRouteId : companyRouteId;
        if ("company".equals(target)) setStates("HUB_ARRIVED", "ARRIVED", "WAITING");
        jdbc.update("UPDATE p_delivery_routes SET manager_id = NULL WHERE id = ?", routeId);
        State before = snapshot();
        assertThat(master(routeId, "IN_TRANSIT").statusCode()).isEqualTo(409);
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"WAITING", "IN_TRANSIT", "CANCELLED"})
    @DisplayName("앞선 배송 경로가 도착하지 않으면 배송 전체가 HUB_ARRIVED여도 다음 출발을 거부한다")
    void previousRouteMustActuallyHaveArrived(String previousStatus) throws Exception {
        // 배송 전체 상태만 신뢰하지 않고 실제 앞선 배송 경로를 검사하는지 분리해서 검증한다.
        setStates("HUB_ARRIVED", previousStatus, "WAITING");
        State before = snapshot();
        assertThat(master(companyRouteId, "IN_TRANSIT").statusCode()).isEqualTo(409);
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"deleted", "missing"})
    @DisplayName("필요한 앞선 배송 경로가 삭제되었거나 없으면 출발을 거부한다")
    void previousRouteMustExistAndBeActive(String scenario) throws Exception {
        setStates("HUB_ARRIVED", "ARRIVED", "WAITING");
        if ("deleted".equals(scenario)) {
            jdbc.update("UPDATE p_delivery_routes SET deleted_at = CURRENT_TIMESTAMP, deleted_by = 'fixture' WHERE id = ?", hubRouteId);
        } else {
            jdbc.update("DELETE FROM p_delivery_routes WHERE id = ?", hubRouteId);
        }
        State before = snapshot();
        assertThat(master(companyRouteId, "IN_TRANSIT").statusCode()).isEqualTo(409);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("배송 경로의 2구간이 허브 간 배송이어도 앞선 배송 경로의 도착 여부를 확인한다")
    void previousRouteValidationDoesNotDependOnCompanyType() throws Exception {
        jdbc.update("UPDATE p_delivery_routes SET sequence = 3 WHERE id = ?", companyRouteId);
        UUID secondHubRoute = UUID.randomUUID();
        insertRoute(secondHubRoute, 2, originHubId, destinationHubId, "HUB_TO_HUB", hubManagerId);
        State before = snapshot();
        assertThat(master(secondHubRoute, "IN_TRANSIT").statusCode()).isEqualTo(409);
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"hub", "company"})
    @DisplayName("WAITING에서 ARRIVED로 출발 단계를 건너뛸 수 없다")
    void cannotSkipDeparture(String target) throws Exception {
        State before = snapshot();
        assertThat(master("hub".equals(target) ? hubRouteId : companyRouteId, "ARRIVED").statusCode()).isEqualTo(409);
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"hub", "company"})
    @DisplayName("ARRIVED에서 IN_TRANSIT으로 되돌릴 수 없다")
    void cannotReverseCompletedRoute(String target) throws Exception {
        boolean company = "company".equals(target);
        setStates(company ? "DELIVERED" : "HUB_ARRIVED", "ARRIVED", company ? "ARRIVED" : "WAITING");
        State before = snapshot();
        assertThat(master(company ? companyRouteId : hubRouteId, "IN_TRANSIT").statusCode()).isEqualTo(409);
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @CsvSource({"WAITING,IN_TRANSIT", "IN_TRANSIT,IN_TRANSIT", "ARRIVED,ARRIVED"})
    @DisplayName("취소된 배송은 신규 변경과 동일 상태 재요청을 모두 거부한다")
    void cancelledDeliveryRejectsChangesAndRetries(String current, String requested) throws Exception {
        setStates("CANCELLED", current, "WAITING");
        State before = snapshot();
        assertThat(master(hubRouteId, requested).statusCode()).isEqualTo(409);
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"IN_TRANSIT", "ARRIVED"})
    @DisplayName("취소된 배송 경로는 상태를 변경할 수 없다")
    void cancelledRouteRejectsChanges(String requested) throws Exception {
        setStates("WAITING", "CANCELLED", "WAITING");
        State before = snapshot();
        assertThat(master(hubRouteId, requested).statusCode()).isEqualTo(409);
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @CsvSource({
            "hub,HUB_ARRIVED,WAITING,WAITING,IN_TRANSIT",
            "hub,WAITING,IN_TRANSIT,WAITING,ARRIVED",
            "company,HUB_IN_TRANSIT,ARRIVED,WAITING,IN_TRANSIT",
            "company,HUB_ARRIVED,ARRIVED,IN_TRANSIT,ARRIVED",
            "hub,DELIVERED,WAITING,ARRIVED,IN_TRANSIT"
    })
    @DisplayName("배송 전체 상태 전이가 거부되면 선행한 배송 경로 변경과 감사 기록도 롤백한다")
    void deliveryTransitionFailureRollsBackRoute(String target, String deliveryStatus,
                                                String hubStatus, String companyStatus, String requested) throws Exception {
        setStates(deliveryStatus, hubStatus, companyStatus);
        State before = snapshot();
        assertThat(master("hub".equals(target) ? hubRouteId : companyRouteId, requested).statusCode()).isEqualTo(409);
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missingDelivery", "missingRoute", "deletedDelivery", "deletedRoute", "foreignRoute"})
    @DisplayName("없는·삭제된·다른 배송 소속 배송 경로는 404로 거부한다")
    void resourceValidation(String scenario) throws Exception {
        UUID requestedDelivery = deliveryId;
        UUID requestedRoute = hubRouteId;
        switch (scenario) {
            case "missingDelivery" -> requestedDelivery = UUID.randomUUID();
            case "missingRoute" -> requestedRoute = UUID.randomUUID();
            case "deletedDelivery" -> jdbc.update("UPDATE p_deliveries SET deleted_at = CURRENT_TIMESTAMP, deleted_by = 'fixture' WHERE id = ?", deliveryId);
            case "deletedRoute" -> jdbc.update("UPDATE p_delivery_routes SET deleted_at = CURRENT_TIMESTAMP, deleted_by = 'fixture' WHERE id = ?", hubRouteId);
            case "foreignRoute" -> {
                UUID otherDelivery = UUID.randomUUID();
                insertDelivery(otherDelivery, originHubId, destinationHubId);
                jdbc.update("UPDATE p_delivery_routes SET delivery_id = ? WHERE id = ?", otherDelivery, hubRouteId);
            }
            default -> throw new IllegalArgumentException(scenario);
        }
        State before = snapshot();
        assertThat(send(requestedDelivery.toString(), requestedRoute.toString(), body("IN_TRANSIT"),
                masterId.toString(), "master", "MASTER").statusCode()).isEqualTo(404);
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"status\":null}", "{\"status\":\"UNKNOWN\"}",
            "{\"status\":\"WAITING\"}", "{\"status\":\"CANCELLED\"}", "{", ""})
    @DisplayName("상태 누락·형식 오류·지원하지 않는 상태 요청을 400으로 거부한다")
    void validatesBody(String body) throws Exception {
        State before = snapshot();
        assertThat(send(deliveryId.toString(), hubRouteId.toString(), body,
                masterId.toString(), "master", "MASTER").statusCode()).isEqualTo(400);
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"delivery", "route"})
    @DisplayName("URL 배송·배송 경로 UUID 형식을 검증한다")
    void validatesPath(String scenario) throws Exception {
        State before = snapshot();
        assertThat(send("delivery".equals(scenario) ? "invalid" : deliveryId.toString(),
                "route".equals(scenario) ? "invalid" : hubRouteId.toString(), body("IN_TRANSIT"),
                masterId.toString(), "master", "MASTER").statusCode()).isEqualTo(400);
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "NULL|master|MASTER|401", "BLANK|master|MASTER|401", "invalid|master|MASTER|400",
            "VALID|NULL|MASTER|401", "VALID|BLANK|MASTER|401", "VALID|master|NULL|401",
            "VALID|master|BLANK|401", "VALID|master|ADMIN|403"
    }, delimiter = '|')
    @DisplayName("필수 헤더·요청자 UUID·허용 역할을 검증한다")
    void validatesRequester(String requester, String username, String role, int expected) throws Exception {
        State before = snapshot();
        assertThat(send(deliveryId.toString(), hubRouteId.toString(), body("IN_TRANSIT"),
                "VALID".equals(requester) ? masterId.toString() : requester, username, role)
                .statusCode()).isEqualTo(expected);
        assertThat(snapshot()).isEqualTo(before);
    }

    private void insertDelivery(UUID id, UUID departure, UUID destination) {
        jdbc.update("""
                INSERT INTO p_deliveries
                (id, order_id, manager_id, departure_hub_id, destination_hub_id, status,
                 address, recipient_name, recipient_phone, created_at, updated_at, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, 'WAITING', 'test address', 'recipient', '010-0000-0000',
                        TIMESTAMP '2026-01-01 09:00:00', TIMESTAMP '2026-01-01 09:00:00', 'fixture', 'fixture')
                """, id, UUID.randomUUID(), companyManagerId, departure, destination);
    }

    private void insertRoute(UUID id, int sequence, UUID departure, UUID destination, String type, UUID manager) {
        jdbc.update("""
                INSERT INTO p_delivery_routes
                (id, delivery_id, sequence, manager_id, departure_hub_id, destination_hub_id, type, status,
                 created_at, updated_at, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'WAITING', TIMESTAMP '2026-01-01 09:00:00',
                        TIMESTAMP '2026-01-01 09:00:00', 'fixture', 'fixture')
                """, id, deliveryId, sequence, manager, departure, destination, type);
    }

    private void setStates(String delivery, String hub, String company) {
        jdbc.update("UPDATE p_deliveries SET status = ? WHERE id = ?", delivery, deliveryId);
        jdbc.update("UPDATE p_delivery_routes SET status = ? WHERE id = ?", hub, hubRouteId);
        jdbc.update("UPDATE p_delivery_routes SET status = ? WHERE id = ?", company, companyRouteId);
    }

    private Map<String, Object> deliveryRow() {
        return jdbc.queryForMap("SELECT * FROM p_deliveries WHERE id = ?", deliveryId);
    }

    private Map<String, Object> routeRow(UUID id) {
        return jdbc.queryForMap("SELECT * FROM p_delivery_routes WHERE id = ?", id);
    }

    private State snapshot() {
        return new State(jdbc.queryForList("SELECT * FROM p_deliveries ORDER BY id"),
                jdbc.queryForList("SELECT * FROM p_delivery_routes ORDER BY id"));
    }

    private void assertUpdatedAudit(Map<String, Object> row, String username) {
        assertThat(row.get("created_by")).isEqualTo("fixture");
        assertThat(row.get("updated_by")).isEqualTo(username);
        assertThat(row.get("created_at").toString()).startsWith("2026-01-01 09:00:00");
        assertThat(row.get("updated_at")).isNotEqualTo(row.get("created_at"));
    }

    private void assertResponse(HttpResponse<String> response, UUID id, String routeStatus, String deliveryStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat((String) JsonPath.read(response.body(), "$.deliveryId")).isEqualTo(deliveryId.toString());
        assertThat((String) JsonPath.read(response.body(), "$.deliveryRouteId")).isEqualTo(id.toString());
        assertThat((String) JsonPath.read(response.body(), "$.routeStatus")).isEqualTo(routeStatus);
        assertThat((String) JsonPath.read(response.body(), "$.deliveryStatus")).isEqualTo(deliveryStatus);
        assertThat(deliveryRow().get("status")).isEqualTo(deliveryStatus);
        assertThat(routeRow(id).get("status")).isEqualTo(routeStatus);
    }

    private String body(String status) {
        return "{\"status\":\"" + status + "\"}";
    }

    private HttpResponse<String> master(UUID routeId, String status) throws Exception {
        return patch(routeId, status, masterId, "master", "MASTER");
    }

    private HttpResponse<String> patch(UUID routeId, String status, UUID requester, String username, String role) throws Exception {
        return send(deliveryId.toString(), routeId.toString(), body(status), requester.toString(), username, role);
    }

    private HttpResponse<String> send(String delivery, String route, String body,
                                      String requester, String username, String role) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/deliveries/" + delivery + "/routes/" + route + "/status"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        addHeader(request, "X-User-Id", requester);
        addHeader(request, "X-User-Name", username);
        addHeader(request, "X-Role-Name", role);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private void addHeader(HttpRequest.Builder request, String name, String value) {
        if (!"NULL".equals(value)) request.header(name, "BLANK".equals(value) ? " " : value);
    }

    private record State(List<Map<String, Object>> deliveries, List<Map<String, Object>> routes) {
    }
}

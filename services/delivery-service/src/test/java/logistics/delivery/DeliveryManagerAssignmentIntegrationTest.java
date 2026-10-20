package logistics.delivery;

import com.jayway.jsonpath.JsonPath;
import feign.FeignException;
import feign.Request;
import feign.Response;
import logistics.delivery.infrastructure.client.HubClient;
import logistics.delivery.infrastructure.client.OrderClient;
import logistics.delivery.infrastructure.client.UserClient;
import logistics.delivery.infrastructure.client.dto.UserDetailResponse;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 실제 HTTP 요청·JPA 트랜잭션·별도 H2 DB로 담당자 배정을 검증한다.
 * UserClient는 가짜 응답으로 대체한다. 실제 사용자 JSON 필드명·포장 구조,
 * Feign 헤더 전송·서비스 인증·삭제 사용자 404 계약은 검증하지 않는다.
 * 가짜 404는 사용자 없음/삭제 처리 계약을 가정한 테스트이며 실제 연동 확인은 별도다.
 * 테스트용 데이터를 JDBC로 준비하며 실제 PostgreSQL에는 접속하지 않는다.
 * 동시 배정·출발 요청의 경쟁 상황은 이 테스트의 검증 범위가 아니다.
 */
@ActiveProfiles("test")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:delivery-manager-assign-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "eureka.client.enabled=false",
                "eureka.client.service-url.defaultZone=http://localhost:8761/eureka/",
                "spring.cloud.discovery.enabled=false"
        }
)
class DeliveryManagerAssignmentIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private UserClient userClient;

    @MockitoBean
    private HubClient hubClient;

    @MockitoBean
    private OrderClient orderClient;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    private UUID deliveryId;
    private UUID hubRouteId;
    private UUID companyRouteId;
    private UUID originHubId;
    private UUID destinationHubId;
    private UUID managerId;
    private UUID requesterId;

    @BeforeEach
    void prepareUnassignedDelivery() {
        jdbc.update("DELETE FROM p_delivery_routes");
        jdbc.update("DELETE FROM p_deliveries");
        deliveryId = UUID.randomUUID();
        hubRouteId = UUID.randomUUID();
        companyRouteId = UUID.randomUUID();
        originHubId = UUID.randomUUID();
        destinationHubId = UUID.randomUUID();
        managerId = UUID.randomUUID();
        requesterId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO p_deliveries
                (id, order_id, departure_hub_id, destination_hub_id, status,
                 address, recipient_name, recipient_phone, created_at, updated_at, created_by, updated_by)
                VALUES (?, ?, ?, ?, 'WAITING', 'test address', 'recipient', '010-0000-0000',
                        TIMESTAMP '2026-01-01 09:00:00', TIMESTAMP '2026-01-01 09:00:00', '00000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000001')
                """, deliveryId, UUID.randomUUID(), originHubId, destinationHubId);
        insertRoute(hubRouteId, 1, originHubId, destinationHubId, "HUB_TO_HUB");
        insertRoute(companyRouteId, 2, destinationHubId, null, "HUB_TO_COMPANY");
    }

    @AfterEach
    void noUnrelatedRemoteCalls() {
        verifyNoInteractions(hubClient, orderClient);
    }

    @Test
    @DisplayName("허브 간 배정은 대상 경로만 변경하고 배송·다른 경로는 유지한다")
    void assignsHubRouteOnly() throws Exception {
        State before = snapshot();
        mockEligibleUser(originHubId);

        assertAssigned(put(hubRouteId), hubRouteId);

        assertThat(deliveryRow()).isEqualTo(before.delivery());
        assertThat(routeRow(companyRouteId)).isEqualTo(before.routes().get(1));
        assertAssignedAudit(routeRow(hubRouteId));
        verify(userClient).getUserDetail(managerId, requesterId, "MASTER");
    }

    @ParameterizedTest
    @ValueSource(strings = {"WAITING", "HUB_IN_TRANSIT", "HUB_ARRIVED"})
    @DisplayName("허용 배송 상태에서 업체 구간 배정은 배송과 경로를 함께 변경한다")
    void assignsCompanyRouteForAllowedDeliveryStates(String deliveryStatus) throws Exception {
        setDeliveryStatus(deliveryStatus);
        if ("HUB_IN_TRANSIT".equals(deliveryStatus)) setRouteStatus(hubRouteId, "IN_TRANSIT");
        if ("HUB_ARRIVED".equals(deliveryStatus)) setRouteStatus(hubRouteId, "ARRIVED");
        Map<String, Object> previousHubRoute = routeRow(hubRouteId);
        mockEligibleUser(destinationHubId);

        assertAssigned(put(companyRouteId), companyRouteId);

        assertAssignedAudit(deliveryRow());
        assertAssignedAudit(routeRow(companyRouteId));
        assertThat(routeRow(hubRouteId)).isEqualTo(previousHubRoute);
        verify(userClient).getUserDetail(managerId, requesterId, "MASTER");
    }

    @Test
    @DisplayName("같은 허브 배송의 유일한 업체 경로에도 배정한다")
    void assignsSingleCompanyRouteForSameHubDelivery() throws Exception {
        jdbc.update("DELETE FROM p_delivery_routes WHERE id = ?", hubRouteId);
        jdbc.update("UPDATE p_deliveries SET departure_hub_id = ? WHERE id = ?", destinationHubId, deliveryId);
        jdbc.update("UPDATE p_delivery_routes SET sequence = 1 WHERE id = ?", companyRouteId);
        mockEligibleUser(destinationHubId);
        assertAssigned(put(companyRouteId), companyRouteId);
        assertAssignedAudit(deliveryRow());
        assertAssignedAudit(routeRow(companyRouteId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"HUB_TO_HUB", "HUB_TO_COMPANY"})
    @DisplayName("동일 담당자 재요청은 사용자 호출 없이 모든 필드·감사 기록을 유지한다")
    void identicalAssignmentSkipsUnavailableUserService(String type) throws Exception {
        UUID routeId = "HUB_TO_COMPANY".equals(type) ? companyRouteId : hubRouteId;
        jdbc.update("UPDATE p_delivery_routes SET manager_id = ? WHERE id = ?", managerId, routeId);
        jdbc.update("UPDATE p_deliveries SET manager_id = ? WHERE id = ?",
                "HUB_TO_COMPANY".equals(type) ? managerId : UUID.randomUUID(), deliveryId);
        State before = snapshot();
        when(userClient.getUserDetail(managerId, requesterId, "MASTER")).thenThrow(feignFailure(503));

        assertAssigned(put(routeId), routeId);

        assertThat(snapshot()).isEqualTo(before);
        verifyNoInteractions(userClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"routeOnly", "deliveryOnly"})
    @DisplayName("업체 구간과 배송 중 한쪽만 동일 담당자이면 사용자 검증 후 나머지를 채운다")
    void repairsPartialCompanyAssignment(String scenario) throws Exception {
        boolean routeOnly = "routeOnly".equals(scenario);
        if (routeOnly) {
            jdbc.update("UPDATE p_delivery_routes SET manager_id = ? WHERE id = ?", managerId, companyRouteId);
        } else {
            jdbc.update("UPDATE p_deliveries SET manager_id = ? WHERE id = ?", managerId, deliveryId);
        }
        Map<String, Object> unchanged = routeOnly ? routeRow(companyRouteId) : deliveryRow();
        mockEligibleUser(destinationHubId);

        assertAssigned(put(companyRouteId), companyRouteId);

        assertThat(deliveryRow().get("manager_id")).isEqualTo(managerId);
        assertThat(routeRow(companyRouteId).get("manager_id")).isEqualTo(managerId);
        assertThat(routeOnly ? routeRow(companyRouteId) : deliveryRow()).isEqualTo(unchanged);
        assertAssignedAudit(routeOnly ? deliveryRow() : routeRow(companyRouteId));
        verify(userClient).getUserDetail(managerId, requesterId, "MASTER");
    }

    @Test
    @DisplayName("배송에 다른 담당자가 있으면 선행한 업체 경로 변경도 롤백한다")
    void rollsBackRouteWhenDeliveryAssignmentConflicts() throws Exception {
        jdbc.update("UPDATE p_deliveries SET manager_id = ? WHERE id = ?", UUID.randomUUID(), deliveryId);
        State before = snapshot();
        mockEligibleUser(destinationHubId);
        assertThat(put(companyRouteId).statusCode()).isEqualTo(409);
        assertThat(snapshot()).isEqualTo(before);
        verify(userClient).getUserDetail(managerId, requesterId, "MASTER");
    }

    @ParameterizedTest
    @ValueSource(strings = {"HUB_TO_HUB", "HUB_TO_COMPANY"})
    @DisplayName("경로에 다른 담당자가 배정되어 있으면 변경하지 않고 409를 반환한다")
    void refusesReplacingExistingRouteManager(String type) throws Exception {
        UUID routeId = "HUB_TO_COMPANY".equals(type) ? companyRouteId : hubRouteId;
        jdbc.update("UPDATE p_delivery_routes SET manager_id = ? WHERE id = ?", UUID.randomUUID(), routeId);
        State before = snapshot();
        mockEligibleUser("HUB_TO_COMPANY".equals(type) ? destinationHubId : originHubId);
        assertThat(put(routeId).statusCode()).isEqualTo(409);
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"COMPANY_IN_TRANSIT", "DELIVERED", "CANCELLED"})
    @DisplayName("허용 범위 밖의 배송 상태는 대상 경로가 WAITING이어도 409로 거부한다")
    void rejectsDisallowedDeliveryStates(String status) throws Exception {
        setDeliveryStatus(status);
        State before = snapshot();
        assertThat(put(companyRouteId).statusCode()).isEqualTo(409);
        assertThat(snapshot()).isEqualTo(before);
        verifyNoInteractions(userClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"IN_TRANSIT", "ARRIVED", "CANCELLED"})
    @DisplayName("대상 경로가 WAITING이 아니면 배정하지 않는다")
    void rejectsNonWaitingRoute(String status) throws Exception {
        setRouteStatus(hubRouteId, status);
        State before = snapshot();
        assertThat(put(hubRouteId).statusCode()).isEqualTo(409);
        assertThat(snapshot()).isEqualTo(before);
        verifyNoInteractions(userClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"delivery", "route"})
    @DisplayName("동일 담당자 재요청도 배송·경로 상태 검증을 생략하지 않는다")
    void retryStillValidatesState(String scenario) throws Exception {
        jdbc.update("UPDATE p_delivery_routes SET manager_id = ? WHERE id = ?", managerId, companyRouteId);
        jdbc.update("UPDATE p_deliveries SET manager_id = ? WHERE id = ?", managerId, deliveryId);
        if ("delivery".equals(scenario)) setDeliveryStatus("COMPANY_IN_TRANSIT");
        else setRouteStatus(companyRouteId, "IN_TRANSIT");
        State before = snapshot();
        assertThat(put(companyRouteId).statusCode()).isEqualTo(409);
        assertThat(snapshot()).isEqualTo(before);
        verifyNoInteractions(userClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missingDelivery", "missingRoute", "deletedDelivery", "deletedRoute", "foreignRoute"})
    @DisplayName("없는·삭제된·다른 배송의 경로는 404로 처리하고 변경하지 않는다")
    void rejectsUnavailableResource(String scenario) throws Exception {
        UUID requestedDelivery = deliveryId;
        UUID requestedRoute = companyRouteId;
        switch (scenario) {
            case "missingDelivery" -> requestedDelivery = UUID.randomUUID();
            case "missingRoute" -> requestedRoute = UUID.randomUUID();
            case "deletedDelivery" -> jdbc.update("UPDATE p_deliveries SET deleted_at = CURRENT_TIMESTAMP, deleted_by = '00000000-0000-4000-8000-000000000001' WHERE id = ?", deliveryId);
            case "deletedRoute" -> jdbc.update("UPDATE p_delivery_routes SET deleted_at = CURRENT_TIMESTAMP, deleted_by = '00000000-0000-4000-8000-000000000001' WHERE id = ?", companyRouteId);
            case "foreignRoute" -> {
                UUID otherDelivery = UUID.randomUUID();
                jdbc.update("""
                        INSERT INTO p_deliveries
                        (id, order_id, departure_hub_id, destination_hub_id, status, address,
                         recipient_name, recipient_phone, created_at, updated_at, created_by, updated_by)
                        VALUES (?, ?, ?, ?, 'WAITING', 'other', 'other', '010-0000-0000',
                                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '00000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000001')
                        """, otherDelivery, UUID.randomUUID(), originHubId, destinationHubId);
                jdbc.update("UPDATE p_delivery_routes SET delivery_id = ? WHERE id = ?", otherDelivery, companyRouteId);
            }
            default -> throw new IllegalArgumentException(scenario);
        }
        State before = snapshot();
        assertThat(send(requestedDelivery.toString(), requestedRoute.toString(), body(), requesterId.toString(), "master", "MASTER").statusCode()).isEqualTo(404);
        assertThat(snapshot()).isEqualTo(before);
        verifyNoInteractions(userClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"notManager", "company", "pending", "otherHub"})
    @DisplayName("배송 담당 자격·HUB 소속·CONFIRMED·출발 허브 일치 조건을 검증한다")
    void rejectsIneligibleUser(String scenario) throws Exception {
        UserDetailResponse user = switch (scenario) {
            case "notManager" -> new UserDetailResponse(managerId, false, "HUB", "CONFIRMED", originHubId);
            case "company" -> new UserDetailResponse(managerId, true, "COMPANY", "CONFIRMED", null);
            case "pending" -> new UserDetailResponse(managerId, true, "HUB", "PENDING", originHubId);
            default -> new UserDetailResponse(managerId, true, "HUB", "CONFIRMED", destinationHubId);
        };
        when(userClient.getUserDetail(managerId, requesterId, "MASTER")).thenReturn(user);
        State before = snapshot();
        assertThat(put(hubRouteId).statusCode()).isEqualTo(422);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("업체 배송은 배송 전체 출발 허브가 아닌 대상 구간의 출발 허브로 검증한다")
    void companyManagerMustBelongToDestinationHub() throws Exception {
        mockEligibleUser(originHubId);
        State before = snapshot();
        assertThat(put(companyRouteId).statusCode()).isEqualTo(422);
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"empty", "missingId", "wrongId", "missingFlag", "missingType", "missingStatus", "missingHub"})
    @DisplayName("사용자 응답이 없거나 불완전하면 검증 불가로 503을 반환한다")
    void rejectsIncompleteMockUserResponse(String scenario) throws Exception {
        UserDetailResponse user = switch (scenario) {
            case "empty" -> null;
            case "missingId" -> new UserDetailResponse(null, true, "HUB", "CONFIRMED", originHubId);
            case "wrongId" -> new UserDetailResponse(UUID.randomUUID(), true, "HUB", "CONFIRMED", originHubId);
            case "missingFlag" -> new UserDetailResponse(managerId, null, "HUB", "CONFIRMED", originHubId);
            case "missingType" -> new UserDetailResponse(managerId, true, null, "CONFIRMED", originHubId);
            case "missingStatus" -> new UserDetailResponse(managerId, true, "HUB", null, originHubId);
            default -> new UserDetailResponse(managerId, true, "HUB", "CONFIRMED", null);
        };
        when(userClient.getUserDetail(managerId, requesterId, "MASTER")).thenReturn(user);
        State before = snapshot();
        assertThat(put(hubRouteId).statusCode()).isEqualTo(503);
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @CsvSource({"404,422", "401,503", "403,503", "500,503", "503,503"})
    @DisplayName("가짜 사용자 API 오류를 배정 오류로 변환하고 DB를 유지한다")
    void mapsMockUserFailures(int remoteStatus, int expectedStatus) throws Exception {
        when(userClient.getUserDetail(managerId, requesterId, "MASTER")).thenThrow(feignFailure(remoteStatus));
        State before = snapshot();
        assertThat(put(hubRouteId).statusCode()).isEqualTo(expectedStatus);
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"managerId\":null}", "{\"managerId\":\"invalid\"}", "{", ""})
    @DisplayName("필수 담당자 ID 누락·UUID 오류·잘못된 JSON은 400으로 거부한다")
    void validatesRequestBody(String body) throws Exception {
        State before = snapshot();
        assertThat(send(deliveryId.toString(), hubRouteId.toString(), body, requesterId.toString(), "master", "MASTER").statusCode()).isEqualTo(400);
        assertThat(snapshot()).isEqualTo(before);
        verifyNoInteractions(userClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"delivery", "route"})
    @DisplayName("URL의 배송·경로 UUID 형식을 검증한다")
    void validatesPathIds(String scenario) throws Exception {
        State before = snapshot();
        assertThat(send("delivery".equals(scenario) ? "invalid" : deliveryId.toString(),
                "route".equals(scenario) ? "invalid" : hubRouteId.toString(),
                body(), requesterId.toString(), "master", "MASTER").statusCode()).isEqualTo(400);
        assertThat(snapshot()).isEqualTo(before);
        verifyNoInteractions(userClient);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "NULL|master|MASTER|401", "BLANK|master|MASTER|401", "invalid|master|MASTER|400",
            "VALID|master|NULL|401",
            "VALID|master|BLANK|401", "VALID|master|USER|403", "VALID|master|ADMIN|403"
    }, delimiter = '|')
    @DisplayName("요청자 헤더와 MASTER 권한을 검증하고 사용자 API를 호출하지 않는다")
    void validatesRequester(String requester, String username, String role, int expected) throws Exception {
        State before = snapshot();
        assertThat(send(deliveryId.toString(), hubRouteId.toString(), body(),
                "VALID".equals(requester) ? requesterId.toString() : requester,
                username, role).statusCode()).isEqualTo(expected);
        assertThat(snapshot()).isEqualTo(before);
        verifyNoInteractions(userClient);
    }

    private void mockEligibleUser(UUID hubId) {
        when(userClient.getUserDetail(managerId, requesterId, "MASTER"))
                .thenReturn(new UserDetailResponse(managerId, true, "HUB", "CONFIRMED", hubId));
    }

    private FeignException feignFailure(int status) {
        Request request = Request.create(Request.HttpMethod.GET, "/api/users/" + managerId,
                Map.of(), new byte[0], StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("getUserDetail", Response.builder()
                .request(request).status(status).reason("mock user failure").build());
    }

    private void insertRoute(UUID id, int sequence, UUID departure, UUID destination, String type) {
        jdbc.update("""
                INSERT INTO p_delivery_routes
                (id, delivery_id, sequence, departure_hub_id, destination_hub_id, type, status,
                 created_at, updated_at, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, 'WAITING', TIMESTAMP '2026-01-01 09:00:00',
                        TIMESTAMP '2026-01-01 09:00:00', '00000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000001')
                """, id, deliveryId, sequence, departure, destination, type);
    }

    private void setDeliveryStatus(String status) {
        jdbc.update("UPDATE p_deliveries SET status = ? WHERE id = ?", status, deliveryId);
    }

    private void setRouteStatus(UUID routeId, String status) {
        jdbc.update("UPDATE p_delivery_routes SET status = ? WHERE id = ?", status, routeId);
    }

    private State snapshot() {
        return new State(deliveryRow(), jdbc.queryForList("SELECT * FROM p_delivery_routes ORDER BY sequence"));
    }

    private Map<String, Object> deliveryRow() {
        return jdbc.queryForMap("SELECT * FROM p_deliveries WHERE id = ?", deliveryId);
    }

    private Map<String, Object> routeRow(UUID routeId) {
        return jdbc.queryForMap("SELECT * FROM p_delivery_routes WHERE id = ?", routeId);
    }

    private void assertAssignedAudit(Map<String, Object> row) {
        assertThat(row.get("manager_id")).isEqualTo(managerId);
        assertThat(row.get("updated_by")).isEqualTo(requesterId);
        assertThat(row.get("created_by")).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000001"));
        assertThat(row.get("created_at").toString()).startsWith("2026-01-01 09:00:00");
        assertThat(row.get("updated_at")).isNotEqualTo(row.get("created_at"));
    }

    private void assertAssigned(HttpResponse<String> response, UUID routeId) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat((String) JsonPath.read(response.body(), "$.deliveryId")).isEqualTo(deliveryId.toString());
        assertThat((String) JsonPath.read(response.body(), "$.deliveryRouteId")).isEqualTo(routeId.toString());
        assertThat((String) JsonPath.read(response.body(), "$.managerId")).isEqualTo(managerId.toString());
    }

    private String body() {
        return "{\"managerId\":\"" + managerId + "\"}";
    }

    private HttpResponse<String> put(UUID routeId) throws Exception {
        return send(deliveryId.toString(), routeId.toString(), body(), requesterId.toString(), "master", "MASTER");
    }

    private HttpResponse<String> send(String delivery, String route, String body,
                                      String requester, String username, String role) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/deliveries/" + delivery + "/routes/" + route + "/assignee"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        addHeader(request, "X-User-Id", requester);
        addHeader(request, "X-Role-Name", role);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private void addHeader(HttpRequest.Builder request, String name, String value) {
        if (!"NULL".equals(value)) request.header(name, "BLANK".equals(value) ? " " : value);
    }

    private record State(Map<String, Object> delivery, List<Map<String, Object>> routes) {
    }
}

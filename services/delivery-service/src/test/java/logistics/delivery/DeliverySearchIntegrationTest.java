package logistics.delivery;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import logistics.delivery.infrastructure.client.HubClient;
import logistics.delivery.infrastructure.client.OrderClient;
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
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 실제 HTTP 요청과 별도 H2 DB로 목록 검색·권한·페이징·정렬을 검증한다.
 * 검색용 데이터는 감사 필드를 포함해 JDBC로 준비하므로 생성 API의 검증 테스트는 아니다.
 * 실제 PostgreSQL, Gateway 인증, 서비스 간 네트워크 연동은 검증하지 않는다.
 * HubClient와 OrderClient는 가짜 객체로 대체하고 목록 검색 중 호출하지 않는지 확인한다.
 */
@ActiveProfiles("test")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:delivery-search-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "eureka.client.enabled=false",
                "eureka.client.service-url.defaultZone=http://localhost:8761/eureka/",
                "spring.cloud.discovery.enabled=false"
        }
)
class DeliverySearchIntegrationTest {

    private static final UUID MANAGER_A = UUID.fromString("88888888-8888-4888-8888-888888888888");
    private static final UUID MANAGER_B = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final UUID HUB_A = UUID.fromString("a7100000-1019-4000-8000-000000000001");
    private static final UUID HUB_B = UUID.fromString("a7100000-1019-4000-8000-000000000002");
    private static final UUID HUB_C = UUID.fromString("a7100000-1019-4000-8000-000000000003");

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private OrderClient orderClient;

    @MockitoBean
    private HubClient hubClient;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeEach
    void prepareSearchFixtures() {
        jdbc.update("DELETE FROM p_delivery_routes");
        jdbc.update("DELETE FROM p_deliveries");
        String[] statuses = {
                "WAITING", "WAITING", "WAITING", "HUB_IN_TRANSIT",
                "HUB_ARRIVED", "COMPANY_IN_TRANSIT", "DELIVERED", "CANCELLED"
        };
        for (int n = 1; n <= 8; n++) {
            UUID manager = n == 1 || n == 4 ? MANAGER_A : MANAGER_B;
            UUID departure = n == 5 ? HUB_C : HUB_A;
            UUID destination = n == 7 ? HUB_C : HUB_B;
            Timestamp created = Timestamp.valueOf(LocalDateTime.of(2026, 10, n == 2 ? 11 : 10 + n, 9, 0));
            Timestamp updated = Timestamp.valueOf(LocalDateTime.of(2026, 10, 19, 20 - n, 0));
            Timestamp deleted = n == 8 ? updated : null;
            jdbc.update("""
                    INSERT INTO p_deliveries
                    (id, order_id, manager_id, departure_hub_id, destination_hub_id, status,
                     address, recipient_name, recipient_phone, created_at, updated_at,
                     created_by, updated_by, deleted_at, deleted_by)
                    VALUES (?, ?, ?, ?, ?, ?, 'test address', 'recipient', '010-0000-0000',
                            ?, ?, '00000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000001', ?, ?)
                    """, deliveryId(n), orderId(n), manager, departure, destination, statuses[n - 1],
                    created, updated, deleted, deleted == null ? null : "00000000-0000-4000-8000-000000000001");

            for (int sequence = 1; sequence <= 2; sequence++) {
                boolean deletedRoute = n == 8 || n == 3 && sequence == 1;
                UUID routeManager = sequence == 2 ? manager
                        : n == 1 || n == 2 || n == 3 || n == 6 ? MANAGER_A : MANAGER_B;
                String routeStatus = switch (n) {
                    case 4 -> sequence == 1 ? "IN_TRANSIT" : "WAITING";
                    case 5 -> sequence == 1 ? "ARRIVED" : "WAITING";
                    case 6 -> sequence == 1 ? "ARRIVED" : "IN_TRANSIT";
                    case 7 -> "ARRIVED";
                    case 8 -> "CANCELLED";
                    default -> deletedRoute ? "CANCELLED" : "WAITING";
                };
                jdbc.update("""
                        INSERT INTO p_delivery_routes
                        (id, delivery_id, sequence, manager_id, departure_hub_id, destination_hub_id,
                         type, status, created_at, updated_at, created_by, updated_by, deleted_at, deleted_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '00000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000001', ?, ?)
                        """, routeId(n, sequence), deliveryId(n), sequence, routeManager,
                        sequence == 1 ? departure : destination, sequence == 1 ? destination : null,
                        sequence == 1 ? "HUB_TO_HUB" : "HUB_TO_COMPANY", routeStatus,
                        created, updated, deletedRoute ? updated : null, deletedRoute ? "00000000-0000-4000-8000-000000000001" : null);
            }
        }
    }

    @AfterEach
    void searchNeverCallsExternalServices() {
        verifyNoInteractions(orderClient, hubClient);
    }

    @Test
    @DisplayName("MASTER 기본 조회는 삭제 배송을 제외하고 생성일·ID 내림차순으로 반환한다")
    void masterReadsActiveDeliveriesWithDefaults() throws Exception {
        DocumentContext body = assertPage(master(""), 7, 7, 6, 5, 4, 3, 2, 1);
        assertThat(body.<Integer>read("$.number")).isZero();
        assertThat(body.<Integer>read("$.size")).isEqualTo(10);
        assertThat(body.<Integer>read("$.totalPages")).isEqualTo(1);
        assertThat(body.<String>read("$.content[0].orderId")).isEqualTo(orderId(7).toString());
        assertThat(body.<String>read("$.content[0].managerId")).isEqualTo(MANAGER_B.toString());
        assertThat(body.<String>read("$.content[0].departureHubId")).isEqualTo(HUB_A.toString());
        assertThat(body.<String>read("$.content[0].destinationHubId")).isEqualTo(HUB_C.toString());
        assertThat(body.<String>read("$.content[0].status")).isEqualTo("DELIVERED");
        assertThat(body.<String>read("$.content[0].createdAt")).isEqualTo("2026-10-17T09:00:00");
        assertThat(body.<String>read("$.content[0].updatedAt")).isEqualTo("2026-10-19T13:00:00");
    }

    @ParameterizedTest
    @CsvSource({"WAITING,3", "HUB_IN_TRANSIT,1", "HUB_ARRIVED,1", "COMPANY_IN_TRANSIT,1", "DELIVERED,1", "CANCELLED,0"})
    @DisplayName("각 배송 상태 조건과 삭제 제외를 적용한다")
    void filtersEveryStatus(String status, int count) throws Exception {
        HttpResponse<String> response = master("?status=" + status);
        assertThat(response.statusCode()).isEqualTo(200);
        DocumentContext body = JsonPath.parse(response.body());
        assertThat(((Number) body.read("$.totalElements")).intValue()).isEqualTo(count);
        assertThat(body.<List<String>>read("$.content[*].status")).hasSize(count).allMatch(status::equals);
    }

    @Test
    @DisplayName("첫 페이지와 마지막 페이지의 목록·전체 건수·페이지 수가 일치한다")
    void paginatesWaitingDeliveriesWithoutDuplicates() throws Exception {
        DocumentContext first = assertPage(master("?status=WAITING&page=0&size=2"), 3, 3, 2);
        DocumentContext last = assertPage(master("?status=WAITING&page=1&size=2"), 3, 1);
        assertThat(first.<Integer>read("$.totalPages")).isEqualTo(2);
        assertThat(first.<Integer>read("$.number")).isZero();
        assertThat(first.<Integer>read("$.size")).isEqualTo(2);
        assertThat(first.<Boolean>read("$.last")).isFalse();
        assertThat(last.<Integer>read("$.totalPages")).isEqualTo(2);
        assertThat(last.<Integer>read("$.number")).isEqualTo(1);
        assertThat(last.<Boolean>read("$.last")).isTrue();
    }

    @Test
    @DisplayName("범위를 벗어난 페이지는 전체 건수를 유지하고 빈 목록을 반환한다")
    void outOfRangePageIsEmpty() throws Exception {
        assertPage(master("?page=20&size=2"), 7);
    }

    @ParameterizedTest
    @CsvSource({"1,7", "100,1"})
    @DisplayName("허용 페이지 크기의 양쪽 경계값을 지원한다")
    void acceptsPageSizeBoundaries(int size, int pages) throws Exception {
        HttpResponse<String> response = master("?size=" + size);
        assertThat(response.statusCode()).isEqualTo(200);
        DocumentContext body = JsonPath.parse(response.body());
        assertThat(body.<Integer>read("$.size")).isEqualTo(size);
        assertThat(body.<Integer>read("$.totalPages")).isEqualTo(pages);
        assertThat(body.<List<Object>>read("$.content")).hasSize(Math.min(size, 7));
    }

    @Test
    @DisplayName("생성일 오름차순에서도 날짜 동률은 ID 내림차순으로 정렬한다")
    void sortsCreatedAtAscendingWithStableTieBreak() throws Exception {
        assertPage(master("?sort=createdAt,asc"), 7, 2, 1, 3, 4, 5, 6, 7);
    }

    @Test
    @DisplayName("생성일 내림차순을 명시적으로 지정할 수 있다")
    void sortsCreatedAtDescending() throws Exception {
        assertPage(master("?sort=createdAt,desc"), 7, 7, 6, 5, 4, 3, 2, 1);
    }

    @Test
    @DisplayName("수정일 내림차순으로 정렬한다")
    void sortsUpdatedAtDescending() throws Exception {
        assertPage(master("?sort=updatedAt,desc"), 7, 1, 2, 3, 4, 5, 6, 7);
    }

    @Test
    @DisplayName("수정일 오름차순과 대문자 정렬 방향을 지원한다")
    void sortsUpdatedAtAscending() throws Exception {
        assertPage(master("?sort=updatedAt,ASC"), 7, 7, 6, 5, 4, 3, 2, 1);
    }

    @Test
    @DisplayName("출발 허브와 도착 허브 조건을 각각 적용한다")
    void filtersDepartureAndDestination() throws Exception {
        assertPage(master("?departureHubId=" + HUB_C), 1, 5);
        assertPage(master("?destinationHubId=" + HUB_C), 1, 7);
    }

    @Test
    @DisplayName("모든 검색 조건을 AND로 조합한다")
    void combinesAllSearchFilters() throws Exception {
        String query = "?status=WAITING&departureHubId=" + HUB_A
                + "&destinationHubId=" + HUB_B + "&managerId=" + MANAGER_A
                + "&orderId=" + orderId(2);
        assertPage(master(query), 1, 2);
        assertPage(master(query.replace("status=WAITING", "status=DELIVERED")), 0);
    }

    @Test
    @DisplayName("주문 ID로 조회하고 없는 주문 및 삭제 배송은 빈 목록으로 반환한다")
    void filtersOrderAndExcludesDeletedDelivery() throws Exception {
        assertPage(master("?orderId=" + orderId(2)), 1, 2);
        assertPage(master("?orderId=" + orderId(8)), 0);
        assertPage(master("?orderId=" + UUID.randomUUID()), 0);
    }

    @Test
    @DisplayName("담당자 OR 활성 구간 담당자 검색은 중복·삭제 경로를 제외한다")
    void searchesBothManagerRelationshipsWithoutDuplicates() throws Exception {
        assertPage(master("?managerId=" + MANAGER_A), 4, 6, 4, 2, 1);
        // 페이지 크기를 작게 해 실제 count 조회에서도 중복이 없는지 검증한다.
        DocumentContext page = assertPage(master("?managerId=" + MANAGER_A + "&size=2&page=1"), 4, 2, 1);
        assertThat(page.<Integer>read("$.totalPages")).isEqualTo(2);
        assertPage(master("?managerId=" + UUID.randomUUID()), 0);
    }

    @Test
    @DisplayName("최종 담당자가 미배정이어도 활성 구간 담당자로 검색할 수 있다")
    void activeRouteManagerWithUnassignedFinalManager() throws Exception {
        jdbc.update("UPDATE p_deliveries SET manager_id = NULL WHERE id = ?", deliveryId(2));
        DocumentContext body = assertPage(master("?orderId=" + orderId(2) + "&managerId=" + MANAGER_A), 1, 2);
        assertThat(body.<Object>read("$.content[0].managerId")).isNull();
    }

    @Test
    @DisplayName("USER는 담당 범위 내 목록·전체 건수만 조회한다")
    void userReadsOnlyAssignedDeliveries() throws Exception {
        assertPage(user("", MANAGER_A), 4, 6, 4, 2, 1);
        DocumentContext page = assertPage(user("?size=2&page=1", MANAGER_A), 4, 2, 1);
        assertThat(page.<Integer>read("$.totalPages")).isEqualTo(2);
        assertPage(user("", UUID.randomUUID()), 0);
    }

    @Test
    @DisplayName("다른 담당자 검색은 USER 자신의 담당 범위를 넓히지 않는다")
    void otherManagerSearchDoesNotExpandAccess() throws Exception {
        assertPage(user("?managerId=" + MANAGER_B, MANAGER_A), 3, 6, 4, 2);
        assertPage(user("?orderId=" + orderId(3), MANAGER_A), 0);
        assertPage(user("?orderId=" + orderId(7), MANAGER_A), 0);
        assertPage(user("?managerId=" + MANAGER_B + "&status=WAITING", MANAGER_A), 1, 2);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "size=0", "size=-1", "size=101", "page=-1", "page=abc", "size=abc",
            "sort=status,asc", "sort=createdAt,invalid", "sort=createdAt",
            "sort=createdAt,", "sort=,asc", "sort=createdAt,asc,extra",
            "status=UNKNOWN", "departureHubId=invalid", "destinationHubId=invalid",
            "managerId=invalid", "orderId=invalid"
    })
    @DisplayName("잘못된 검색값·페이지·정렬은 보정하지 않고 400으로 거부한다")
    void invalidQueryReturns400(String query) throws Exception {
        assertThat(master("?" + query).statusCode()).isEqualTo(400);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "NULL|reader|MASTER|401",
            "BLANK|reader|MASTER|401",
            "invalid|reader|MASTER|400",
            "VALID|reader|NULL|401",
            "VALID|reader|BLANK|401",
            "VALID|reader|ADMIN|403"
    }, delimiter = '|')
    @DisplayName("필수 사용자 헤더·UUID 형식·허용 역할을 검증한다")
    void validatesUserHeaders(String user, String name, String role, int expected) throws Exception {
        String id = "VALID".equals(user) ? MANAGER_A.toString() : user;
        assertThat(get("", id, name, role).statusCode()).isEqualTo(expected);
    }

    private DocumentContext assertPage(HttpResponse<String> response, int total, int... expectedNumbers) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        DocumentContext body = JsonPath.parse(response.body());
        assertThat(((Number) body.read("$.totalElements")).intValue()).isEqualTo(total);
        List<String> ids = body.read("$.content[*].deliveryId");
        String[] expectedIds = new String[expectedNumbers.length];
        for (int i = 0; i < expectedNumbers.length; i++) {
            expectedIds[i] = deliveryId(expectedNumbers[i]).toString();
        }
        assertThat(ids).containsExactly(expectedIds).doesNotHaveDuplicates();
        assertThat(body.<Integer>read("$.numberOfElements")).isEqualTo(expectedNumbers.length);
        return body;
    }

    private HttpResponse<String> master(String query) throws Exception {
        return get(query, UUID.fromString("99999999-9999-4999-8999-999999999999").toString(), "reader", "MASTER");
    }

    private HttpResponse<String> user(String query, UUID userId) throws Exception {
        return get(query, userId.toString(), "reader", "USER");
    }

    private HttpResponse<String> get(String query, String userId, String name, String role) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/deliveries" + query))
                .timeout(Duration.ofSeconds(10)).GET();
        addHeader(request, "X-User-Id", userId);
        addHeader(request, "X-Role-Name", role);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private void addHeader(HttpRequest.Builder request, String name, String value) {
        if (!"NULL".equals(value)) request.header(name, "BLANK".equals(value) ? " " : value);
    }

    private UUID deliveryId(int number) {
        return UUID.fromString("d7100000-1019-4000-8000-%012d".formatted(number));
    }

    private UUID orderId(int number) {
        return UUID.fromString("07100000-1019-4000-8000-%012d".formatted(number));
    }

    private UUID routeId(int delivery, int sequence) {
        return UUID.fromString("e7100000-1019-4000-8000-%012d".formatted(delivery * 10 + sequence));
    }
}

package logistics.delivery;

import com.jayway.jsonpath.JsonPath;
import feign.FeignException;
import feign.Request;
import feign.Response;
import logistics.delivery.infrastructure.client.HubClient;
import logistics.delivery.infrastructure.client.OrderClient;
import logistics.delivery.infrastructure.client.UserClient;
import logistics.delivery.infrastructure.client.dto.HubRouteResponse;
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
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 외부 API는 대체하고 실제 HTTP 요청·응답 및 테스트 전용 H2 저장 결과를 검증한다. */
@ActiveProfiles("test")
@Import(DeliveryExceptionIntegrationTest.FailureController.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:delivery-exception-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop",
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
        "eureka.client.service-url.defaultZone=http://localhost:8761/eureka/"
})
class DeliveryExceptionIntegrationTest {
    @LocalServerPort
    private int port;
    @Autowired
    private DeliveryRepository deliveries;
    @Autowired
    private DeliveryRouteRepository routes;
    @MockitoBean
    private HubClient hubClient;
    @MockitoBean
    private OrderClient orderClient;
    @MockitoBean
    private UserClient userClient;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final UUID requester = UUID.randomUUID();
    private UUID orderId;
    private UUID origin;
    private UUID destination;

    /** 실제 PostgreSQL을 사용하지 않고 테스트 H2의 데이터만 초기화한다. */
    @BeforeEach
    void setUp() {
        routes.deleteAllInBatch();
        deliveries.deleteAllInBatch();
        orderId = UUID.randomUUID();
        origin = UUID.randomUUID();
        destination = UUID.randomUUID();
    }

    @ParameterizedTest
    @CsvSource({"missingId,401,USER_ID_REQUIRED", "blankId,401,USER_ID_REQUIRED",
            "invalidId,400,INVALID_USER_ID", "missingRole,401,ROLE_REQUIRED", "blankRole,401,ROLE_REQUIRED"})
    @DisplayName("인터셉터 오류도 공통 JSON으로 응답한다")
    void headerErrorsUseCommonResponse(String scenario, int status, String code) throws Exception {
        HttpRequest.Builder request = builder("/api/deliveries");
        if (!scenario.equals("missingId")) request.header("X-User-Id", switch (scenario) {
            case "blankId" -> " ";
            case "invalidId" -> "invalid";
            default -> requester.toString();
        });
        if (!scenario.equals("missingRole")) request.header("X-Role-Name", scenario.equals("blankRole") ? " " : "MASTER");
        assertError(send(request.GET().build()), status, code);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "", "{\"orderId\":\"invalid\"}"})
    @DisplayName("잘못된 JSON과 UUID는 파싱 상세 정보 없이 400으로 응답한다")
    void invalidBody(String body) throws Exception {
        assertError(post(body), 400, "INVALID_REQUEST_BODY");
        assertEmptyDatabase();
    }

    @Test
    @DisplayName("필수값·길이 검증 실패는 필드명을 반환하고 원본 입력값은 노출하지 않는다")
    void validationErrorsHideRejectedValues() throws Exception {
        String sensitiveAddress = "PRIVATE_ADDRESS_".repeat(25);
        HttpResponse<String> response = post(body(origin).replace("test address", sensitiveAddress)
                .replace("\"recipient\"", "\"\""));
        assertError(response, 400, "VALIDATION_FAILED");
        List<String> fields = JsonPath.read(response.body(), "$.errors[*].field");
        assertThat(fields).contains("address", "recipientName");
        assertThat(response.body()).doesNotContain(sensitiveAddress, "rejectedValue");
        assertEmptyDatabase();
    }

    @ParameterizedTest
    @ValueSource(strings = {"?managerId=invalid", "?status=UNKNOWN"})
    @DisplayName("검색 DTO 바인딩 실패는 내부 변환 메시지를 노출하지 않는다")
    void searchBindingErrors(String query) throws Exception {
        HttpResponse<String> response = get("/api/deliveries" + query);
        assertError(response, 400, "VALIDATION_FAILED");
        assertThat(response.body()).doesNotContain("Failed to convert", "java.util.UUID");
        assertThat((List<?>) JsonPath.read(response.body(), "$.errors")).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/deliveries/invalid", "/api/deliveries?page=abc"})
    @DisplayName("URL·단일 쿼리 파라미터의 타입 오류를 구분한다")
    void parameterErrors(String path) throws Exception {
        assertError(get(path), 400, "INVALID_PARAMETER");
    }

    @ParameterizedTest
    @CsvSource({"?page=-1,INVALID_PAGE", "?size=0,INVALID_PAGE_SIZE", "?size=101,INVALID_PAGE_SIZE",
            "?sort=id%2Cdesc,INVALID_SORT", "?sort=createdAt%2Cwrong,INVALID_SORT", "?sort=createdAt,INVALID_SORT"})
    @DisplayName("페이지·정렬 업무 검증은 기존 400 정책과 오류 코드를 유지한다")
    void pageErrors(String query, String code) throws Exception {
        assertError(get("/api/deliveries" + query), 400, code);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 404, 500, 503})
    @DisplayName("허브 API 실패는 원인 본문을 숨기고 503으로 처리하며 배송을 저장하지 않는다")
    void hubFailure(int remoteStatus) throws Exception {
        when(hubClient.getRoute(origin, destination)).thenThrow(failure(remoteStatus));
        HttpResponse<String> response = post(body(destination));
        assertError(response, 503, "HUB_SERVICE_UNAVAILABLE");
        assertThat(response.body()).doesNotContain("SECRET_REMOTE_BODY");
        assertEmptyDatabase();
    }

    @ParameterizedTest
    @ValueSource(strings = {"empty", "distance", "duration", "origin", "destination", "negativeDistance", "negativeDuration"})
    @DisplayName("누락·허브 불일치·음수인 허브 응답을 503으로 거부한다")
    void invalidHubResponse(String scenario) throws Exception {
        HubRouteResponse response = scenario.equals("empty") ? null : new HubRouteResponse(
                scenario.equals("destination") ? UUID.randomUUID() : destination, "destination",
                scenario.equals("distance") ? null : new BigDecimal(scenario.equals("negativeDistance") ? "-1" : "10.5"),
                scenario.equals("duration") ? null : scenario.equals("negativeDuration") ? -1 : 20,
                UUID.randomUUID(), scenario.equals("origin") ? UUID.randomUUID() : origin, "origin");
        when(hubClient.getRoute(origin, destination)).thenReturn(response);
        assertError(post(body(destination)), 503, "INVALID_HUB_SERVICE_RESPONSE");
        assertEmptyDatabase();
    }

    @Test
    @DisplayName("정상 허브 응답이면 배송과 배송 경로의 2구간을 저장한다")
    void validHubResponse() throws Exception {
        when(hubClient.getRoute(origin, destination)).thenReturn(new HubRouteResponse(destination, "destination",
                new BigDecimal("10.5"), 20, UUID.randomUUID(), origin, "origin"));
        assertThat(post(body(destination)).statusCode()).isEqualTo(201);
        assertThat(deliveries.count()).isEqualTo(1);
        assertThat(routes.findAll()).hasSize(2).anySatisfy(route -> {
            assertThat(route.getExpectedDistance()).isEqualByComparingTo("10.5");
            assertThat(route.getExpectedDuration()).isEqualTo(20);
        });
    }

    @Test
    @DisplayName("동일 요청은 200으로 유지하고 다른 내용은 409와 생성 충돌 코드로 반환한다")
    void creationConflict() throws Exception {
        assertThat(post(body(origin)).statusCode()).isEqualTo(201);
        assertThat(post(body(origin)).statusCode()).isEqualTo(200);
        assertError(post(body(origin).replace("test address", "another address")), 409, "DELIVERY_CREATION_CONFLICT");
        assertThat(deliveries.count()).isEqualTo(1);
        assertThat(routes.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 404})
    @DisplayName("주문 조회 거절과 실제 없는 배송의 응답이 같아 존재 여부를 노출하지 않는다")
    void orderDenialHidesExistence(int remoteStatus) throws Exception {
        assertThat(post(body(origin)).statusCode()).isEqualTo(201);
        UUID id = deliveries.findAll().getFirst().getId();
        when(orderClient.getOrderDetail(orderId, requester, "USER")).thenThrow(failure(remoteStatus));
        HttpResponse<String> denied = send(authenticated("/api/deliveries/" + id, "USER").GET().build());
        HttpResponse<String> missing = get("/api/deliveries/" + UUID.randomUUID());
        assertError(denied, 404, "DELIVERY_NOT_FOUND");
        assertThat(denied.body()).isEqualTo(missing.body());
        verify(orderClient).getOrderDetail(orderId, requester, "USER");
    }

    @Test
    @DisplayName("다른 주문자도 실제 없는 배송과 같은 오류를 받는다")
    void differentOrderOwnerHidesExistence() throws Exception {
        assertThat(post(body(origin)).statusCode()).isEqualTo(201);
        UUID id = deliveries.findAll().getFirst().getId();
        when(orderClient.getOrderDetail(orderId, requester, "USER"))
                .thenReturn(new OrderDetailResponse(orderId, UUID.randomUUID()));
        HttpResponse<String> denied = send(authenticated("/api/deliveries/" + id, "USER").GET().build());
        assertError(denied, 404, "DELIVERY_NOT_FOUND");
        assertThat(denied.body()).isEqualTo(get("/api/deliveries/" + UUID.randomUUID()).body());
    }

    @Test
    @DisplayName("지원하지 않는 메서드는 405와 Allow 헤더를 유지한다")
    void methodNotAllowed() throws Exception {
        HttpResponse<String> response = send(authenticated("/internal/deliveries", "MASTER")
                .PUT(HttpRequest.BodyPublishers.noBody()).build());
        assertError(response, 405, "METHOD_NOT_ALLOWED");
        assertThat(response.headers().firstValue("Allow").orElse("")).contains("POST");
    }

    @Test
    @DisplayName("지원하지 않는 본문 형식은 500 대신 415로 유지한다")
    void unsupportedContentType() throws Exception {
        HttpResponse<String> response = send(authenticated("/internal/deliveries", "MASTER")
                .header("Content-Type", "text/plain").POST(HttpRequest.BodyPublishers.ofString("text")).build());
        assertError(response, 415, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    @DisplayName("존재하지 않는 API는 500 대신 공통 404로 응답한다")
    void missingEndpoint() throws Exception {
        assertError(get("/api/deliveries/not/an/endpoint"), 404, "ENDPOINT_NOT_FOUND");
    }

    @ParameterizedTest
    @ValueSource(strings = {"argument", "database"})
    @DisplayName("알 수 없는 인자·DB 오류를 업무 오류로 오인하지 않고 상세 내용 없는 500으로 반환한다")
    void unexpectedFailure(String kind) throws Exception {
        HttpResponse<String> response = get("/api/deliveries/test-errors/" + kind);
        assertError(response, 500, "INTERNAL_SERVER_ERROR");
        assertThat(response.body()).doesNotContain("SECRET", "SQL", "constraint", "IllegalArgumentException");
    }

    @Test
    @DisplayName("Swagger에 공통 오류 응답 스키마가 연결되어 있다")
    void swaggerErrorSchema() throws Exception {
        HttpResponse<String> response = get("/v3/api-docs");
        assertThat(response.statusCode()).isEqualTo(200);
        String ref = JsonPath.read(response.body(),
                "$.paths['/internal/deliveries'].post.responses['409'].content['application/json'].schema['$ref']");
        assertThat(ref).endsWith("/DeliveryErrorResponse");
    }

    /** 정상 생성 요청 본문을 만들며 목적지에 출발 허브를 넣으면 허브 API를 생략한다. */
    private String body(UUID destinationId) {
        return """
                {"orderId":"%s","departureHubId":"%s","destinationHubId":"%s",
                 "address":"test address","recipientName":"recipient","recipientPhone":"010-0000-0000"}
                """.formatted(orderId, origin, destinationId);
    }

    /** 외부 통신 실패 응답은 Mockito로만 준비하며 실제 외부 서버는 호출하지 않는다. */
    private FeignException failure(int status) {
        Request request = Request.create(Request.HttpMethod.GET, "/mock", Map.of(), new byte[0], StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("mock", Response.builder().request(request).status(status)
                .reason("mock failure").body("SECRET_REMOTE_BODY", StandardCharsets.UTF_8).build());
    }

    /** 공통 오류 응답의 상태·코드·메시지·목록 형식을 검증한다. */
    private void assertError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo(code);
        assertThat((String) JsonPath.read(response.body(), "$.message")).isNotBlank();
        assertThat((List<?>) JsonPath.read(response.body(), "$.errors")).isNotNull();
    }

    /** 오류 발생 후 배송과 배송 경로가 남지 않는지 확인한다. */
    private void assertEmptyDatabase() {
        assertThat(deliveries.count()).isZero();
        assertThat(routes.count()).isZero();
    }

    /** 사용자 ID와 역할 두 헤더만으로 POST 요청을 보낸다. */
    private HttpResponse<String> post(String body) throws Exception {
        return send(authenticated("/internal/deliveries", "USER").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build());
    }

    /** MASTER 권한으로 GET 요청을 보낸다. */
    private HttpResponse<String> get(String path) throws Exception {
        return send(authenticated(path, "MASTER").GET().build());
    }

    /** 실제 인증을 대체한 원 요청자 정보를 테스트 요청에 설정한다. */
    private HttpRequest.Builder authenticated(String path, String role) {
        return builder(path).header("X-User-Id", requester.toString()).header("X-Role-Name", role);
    }

    /** 테스트 서버의 HTTP 요청을 생성한다. */
    private HttpRequest.Builder builder(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(15));
    }

    /** 응답을 UTF-8로 읽는다. */
    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** 테스트 컨텍스트에만 등록되는 예상하지 못한 오류 재현용 컨트롤러. */
    @RestController
    static class FailureController {
        /** 일반 인자 오류와 DB 오류를 발생시켜 공통 500 응답을 검증한다. */
        @GetMapping("/api/deliveries/test-errors/{kind}")
        public void fail(@PathVariable("kind") String kind) {
            if (kind.equals("database")) {
                throw new DataIntegrityViolationException("SECRET SQL constraint");
            }
            throw new IllegalArgumentException("SECRET internal argument");
        }
    }
}

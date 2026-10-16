package logistics.delivery;

import com.jayway.jsonpath.JsonPath;
import logistics.delivery.common.entity.BaseEntity;
import logistics.delivery.domain.entity.Delivery;
import logistics.delivery.domain.entity.DeliveryRoute;
import logistics.delivery.domain.enums.DeliveryRouteStatus;
import logistics.delivery.domain.enums.DeliveryStatus;
import logistics.delivery.domain.enums.RouteType;
import logistics.delivery.infrastructure.client.HubClient;
import logistics.delivery.infrastructure.repository.DeliveryRepository;
import logistics.delivery.infrastructure.repository.DeliveryRouteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@ActiveProfiles("test")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:delivery-creation-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "eureka.client.enabled=false",
                "eureka.client.service-url.defaultZone=http://localhost:8761/eureka/",
                "spring.cloud.discovery.enabled=false"
        }
)
class DeliveryCreationIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private DeliveryRouteRepository deliveryRouteRepository;

    // 같은 허브 배송은 허브 서비스를 호출하지 않아야 한다.
    @MockitoBean
    private HubClient hubClient;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private UUID orderId;
    private UUID hubId;

    @BeforeEach
    void setUp() {
        // 테스트 전용 H2 DB만 정리한다. 실제 PostgreSQL에는 접속하지 않는다.
        deliveryRouteRepository.deleteAllInBatch();
        deliveryRepository.deleteAllInBatch();
        orderId = UUID.randomUUID();
        hubId = UUID.randomUUID();
    }

    @Test
    @DisplayName("신규 요청은 201과 배송 ID를 반환하고 배송·업체 경로·감사 기록을 저장한다")
    void createsDeliveryAndCompanyRoute() throws Exception {
        HttpResponse<String> response = post("서울 테스트 주소", "testuser");

        assertThat(response.statusCode()).isEqualTo(201);
        Delivery delivery = findDelivery();
        assertThat(deliveryId(response)).isEqualTo(delivery.getId().toString());
        assertThat((String) JsonPath.read(response.body(), "$.orderId"))
                .isEqualTo(orderId.toString());
        assertThat((String) JsonPath.read(response.body(), "$.status"))
                .isEqualTo("WAITING");
        assertThat(deliveryRepository.count()).isEqualTo(1);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.WAITING);
        assertThat(delivery.getAddress()).isEqualTo("서울 테스트 주소");
        assertThat(delivery.getDepartureHubId()).isEqualTo(hubId);
        assertThat(delivery.getDestinationHubId()).isEqualTo(hubId);
        assertThat(delivery.getManagerId()).isNull();
        assertInitialAudit(delivery);

        assertThat(deliveryRouteRepository.count()).isEqualTo(1);
        DeliveryRoute route = findRoute();
        assertThat(route.getSequence()).isEqualTo(1);
        assertThat(route.getType()).isEqualTo(RouteType.HUB_TO_COMPANY);
        assertThat(route.getStatus()).isEqualTo(DeliveryRouteStatus.WAITING);
        assertThat(route.getDepartureHubId()).isEqualTo(hubId);
        assertThat(route.getDestinationHubId()).isNull();
        assertThat(route.getExpectedDistance()).isNull();
        assertThat(route.getExpectedDuration()).isNull();
        assertThat(route.getActualDistance()).isNull();
        assertThat(route.getActualDuration()).isNull();
        assertInitialAudit(route);
        verifyNoInteractions(hubClient);
    }

    @Test
    @DisplayName("동일 요청은 200과 기존 배송 ID를 반환하며 배송·경로·최초 감사 기록을 유지한다")
    void returnsExistingDeliveryForIdenticalRequest() throws Exception {
        HttpResponse<String> first = post("서울 테스트 주소", "testuser");
        assertThat(first.statusCode()).isEqualTo(201);
        DeliverySnapshot beforeDelivery = DeliverySnapshot.from(findDelivery());
        RouteSnapshot beforeRoute = RouteSnapshot.from(findRoute());

        // 요청자를 바꿔도 재요청이 최초 생성자·수정자를 덮어쓰지 않아야 한다.
        HttpResponse<String> retry = post("서울 테스트 주소", "retryuser");

        assertThat(retry.statusCode()).isEqualTo(200);
        assertThat(deliveryId(retry)).isEqualTo(deliveryId(first));
        assertThat(deliveryRepository.count()).isEqualTo(1);
        assertThat(deliveryRouteRepository.count()).isEqualTo(1);
        assertThat(DeliverySnapshot.from(findDelivery())).isEqualTo(beforeDelivery);
        assertThat(RouteSnapshot.from(findRoute())).isEqualTo(beforeRoute);
        verifyNoInteractions(hubClient);
    }

    @Test
    @DisplayName("같은 주문의 주소가 다른 요청은 409로 거절하고 기존 데이터·감사 기록을 유지한다")
    void rejectsConflictingRequest() throws Exception {
        HttpResponse<String> first = post("서울 테스트 주소", "testuser");
        assertThat(first.statusCode()).isEqualTo(201);
        DeliverySnapshot beforeDelivery = DeliverySnapshot.from(findDelivery());
        RouteSnapshot beforeRoute = RouteSnapshot.from(findRoute());

        HttpResponse<String> conflict = post("변경된 주소", "retryuser");

        assertThat(conflict.statusCode()).isEqualTo(409);
        assertThat(deliveryRepository.count()).isEqualTo(1);
        assertThat(deliveryRouteRepository.count()).isEqualTo(1);
        assertThat(DeliverySnapshot.from(findDelivery())).isEqualTo(beforeDelivery);
        assertThat(RouteSnapshot.from(findRoute())).isEqualTo(beforeRoute);
        verifyNoInteractions(hubClient);
    }

    private HttpResponse<String> post(String address, String username) throws Exception {
        String body = """
                {
                  "orderId": "%s",
                  "departureHubId": "%s",
                  "destinationHubId": "%s",
                  "address": "%s",
                  "recipientName": "테스트 수령인",
                  "recipientPhone": "010-1234-5678"
                }
                """.formatted(orderId, hubId, hubId, address);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/internal/deliveries"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("X-User-Name", username)
                .header("X-Role-Name", "USER")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        return httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private Delivery findDelivery() {
        return deliveryRepository.findByOrderId(orderId).orElseThrow();
    }

    private DeliveryRoute findRoute() {
        return deliveryRouteRepository.findAll().getFirst();
    }

    private String deliveryId(HttpResponse<String> response) {
        return JsonPath.read(response.body(), "$.deliveryId");
    }

    private void assertInitialAudit(BaseEntity entity) {
        assertThat(entity.getCreatedBy()).isEqualTo("testuser");
        assertThat(entity.getUpdatedBy()).isEqualTo("testuser");
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isEqualTo(entity.getCreatedAt());
        assertThat(entity.getDeletedAt()).isNull();
        assertThat(entity.getDeletedBy()).isNull();
    }

    private record DeliverySnapshot(
            UUID id, String address, DeliveryStatus status,
            String createdBy, String updatedBy,
            LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        static DeliverySnapshot from(Delivery delivery) {
            return new DeliverySnapshot(delivery.getId(), delivery.getAddress(),
                    delivery.getStatus(), delivery.getCreatedBy(), delivery.getUpdatedBy(),
                    delivery.getCreatedAt(), delivery.getUpdatedAt());
        }
    }

    private record RouteSnapshot(
            UUID id, Integer sequence, RouteType type, DeliveryRouteStatus status,
            String createdBy, String updatedBy,
            LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        static RouteSnapshot from(DeliveryRoute route) {
            return new RouteSnapshot(route.getId(), route.getSequence(), route.getType(),
                    route.getStatus(), route.getCreatedBy(), route.getUpdatedBy(),
                    route.getCreatedAt(), route.getUpdatedAt());
        }
    }
}

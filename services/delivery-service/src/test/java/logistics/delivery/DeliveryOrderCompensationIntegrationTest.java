package logistics.delivery;

import com.jayway.jsonpath.JsonPath;
import logistics.delivery.domain.enums.DeliveryStatus;
import logistics.delivery.infrastructure.client.HubClient;
import logistics.delivery.infrastructure.client.dto.HubRouteResponse;
import logistics.delivery.infrastructure.repository.DeliveryRepository;
import logistics.delivery.infrastructure.repository.DeliveryRouteRepository;
import logistics.delivery.infrastructure.repository.DeliveryOrderControlRepository;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 주문 ID 기준 취소, 삭제 API를 HTTP부터 DB 변경까지 검증한다.
 * 테스트 전용 H2와 가짜 허브 응답을 사용하며 실제 주문 서비스 연동은 검증하지 않는다.
 * 동시 요청은 실제 HTTP와 H2의 행 잠금으로 검증한다. PostgreSQL 잠금 동작은 별도 연동 검증이 필요하다.
 * 실제 주문 서비스의 응답 유실, 보상 재시도 스케줄링은 검증하지 않는다.
 */
@ActiveProfiles("test")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:delivery-order-compensation-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "eureka.client.enabled=false",
                "eureka.client.service-url.defaultZone=http://localhost:8761/eureka/",
                "spring.cloud.discovery.enabled=false"
        }
)
class DeliveryOrderCompensationIntegrationTest {

    private static final UUID CREATOR = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID REQUESTER = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID RETRY_REQUESTER = UUID.fromString("33333333-3333-4333-8333-333333333333");

    @LocalServerPort
    private int port;
    @Autowired
    private DeliveryRepository deliveryRepository;
    @Autowired
    private DeliveryRouteRepository deliveryRouteRepository;
    @Autowired
    private DeliveryOrderControlRepository controlRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private HubClient hubClient;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    /** 각 테스트 전에 테스트 전용 H2의 배송 데이터만 초기화한다. */
    @BeforeEach
    void cleanDatabase() {
        deliveryRouteRepository.deleteAllInBatch();
        deliveryRepository.deleteAllInBatch();
        controlRepository.deleteAllInBatch();
    }

    /** 주문 ID로 배송을 선택하여 다른 주문의 배송을 유지하면서 모든 배송 경로를 취소한다. */
    @Test
    @DisplayName("주문 ID 취소는 해당 배송과 배송 경로만 취소하고 원 요청자 UUID를 기록한다")
    void cancelsOnlyMatchingOrder() throws Exception {
        Fixture target = createDelivery();
        Fixture other = createDelivery();
        Snapshot before = snapshot(target);
        Snapshot otherBefore = snapshot(other);

        HttpResponse<String> response = cancel(target, REQUESTER);

        assertCancellation(response, target);
        Snapshot after = snapshot(target);
        assertCancelled(before.delivery(), after.delivery());
        assertThat(after.routes()).hasSize(2);
        for (int i = 0; i < after.routes().size(); i++) {
            assertCancelled(before.routes().get(i), after.routes().get(i));
        }
        assertThat(snapshot(other)).isEqualTo(otherBefore);
        assertThat(deliveryRepository.count()).isEqualTo(2);
        assertThat(deliveryRouteRepository.count()).isEqualTo(4);
    }

    /** 다른 요청자의 취소 재요청에서도 최초 취소 결과와 감사 기록을 유지한다. */
    @Test
    @DisplayName("동일 주문 취소 재요청은 응답과 최초 감사 기록을 유지한다")
    void repeatedCancellationPreservesAudit() throws Exception {
        Fixture fixture = createDelivery();
        HttpResponse<String> first = cancel(fixture, REQUESTER);
        assertCancellation(first, fixture);
        Snapshot beforeRetry = snapshot(fixture);

        HttpResponse<String> repeated = cancel(fixture, RETRY_REQUESTER);

        assertCancellation(repeated, fixture);
        assertThat(repeated.body()).isEqualTo(first.body());
        assertThat(snapshot(fixture)).isEqualTo(beforeRetry);
    }

    /** 취소, 삭제 재요청과 삭제 후 취소 요청에서 최초 논리 삭제 기록을 보존한다. */
    @Test
    @DisplayName("주문 ID 삭제는 배송과 배송 경로를 함께 삭제하고 재요청은 최초 기록을 유지한다")
    void deletesAndPreservesFirstDeletion() throws Exception {
        Fixture fixture = createDelivery();
        assertCancellation(cancel(fixture, REQUESTER), fixture);
        Snapshot before = snapshot(fixture);

        HttpResponse<String> response = delete(fixture, REQUESTER);

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(response.body()).isEmpty();
        Snapshot deleted = snapshot(fixture);
        Object deletedAt = deleted.delivery().get("DELETED_AT");
        assertThat(deletedAt).isNotNull();
        assertDeleted(before.delivery(), deleted.delivery(), deletedAt);
        assertThat(deleted.routes()).hasSize(2);
        for (int i = 0; i < deleted.routes().size(); i++) {
            assertDeleted(before.routes().get(i), deleted.routes().get(i), deletedAt);
        }

        HttpResponse<String> repeated = delete(fixture, RETRY_REQUESTER);
        assertThat(repeated.statusCode()).isEqualTo(204);
        assertThat(repeated.body()).isEmpty();
        assertThat(snapshot(fixture)).isEqualTo(deleted);
        assertError(cancel(fixture, RETRY_REQUESTER), 404, "DELIVERY_NOT_FOUND");
        assertThat(snapshot(fixture)).isEqualTo(deleted);
        assertThat(deliveryRepository.count()).isEqualTo(1);
        assertThat(deliveryRouteRepository.count()).isEqualTo(2);
    }

    /** 배송 없는 주문은 취소 시 생성 차단, 삭제 시 취소 선행 정책을 적용한다. */
    @ParameterizedTest
    @ValueSource(strings = {"POST", "DELETE"})
    @DisplayName("배송 없는 주문은 취소 200 또는 삭제 409이며 다른 주문의 배송을 변경하지 않는다")
    void missingOrderDoesNotChangeExistingDelivery(String method) throws Exception {
        Fixture fixture = createDelivery();
        Snapshot before = snapshot(fixture);
        for (UUID id : List.of(UUID.randomUUID(), fixture.deliveryId())) {
            HttpResponse<String> response = send(method, orderPath(id.toString(), method),
                    REQUESTER.toString(), "USER", null);
            if ("POST".equals(method)) {
                assertCreationBlocked(response, id);
                assertThat(controlSnapshot(id).get("STATUS")).isEqualTo("CANCELLED");
            } else {
                assertError(response, 409, "DELIVERY_DELETION_NOT_ALLOWED");
                assertThat(controlSnapshot(id).get("STATUS")).isEqualTo("ALLOWED");
            }
            assertThat(snapshot(fixture)).isEqualTo(before);
        }
    }

    /** 출발 이후 배송의 취소는 기존 배송 상태 정책에 따라 거절한다. */
    @ParameterizedTest
    @EnumSource(value = DeliveryStatus.class, names = {
            "HUB_IN_TRANSIT", "HUB_ARRIVED", "COMPANY_IN_TRANSIT", "DELIVERED"
    })
    @DisplayName("주문 ID 취소도 출발 이후에는 409이며 기존 데이터를 유지한다")
    void rejectsCancellationAfterDeparture(DeliveryStatus status) throws Exception {
        Fixture fixture = createDelivery();
        jdbc.update("UPDATE p_deliveries SET status = ? WHERE id = ?", status.name(), fixture.deliveryId());
        Snapshot before = snapshot(fixture);

        assertError(cancel(fixture, REQUESTER), 409, "DELIVERY_CANCELLATION_NOT_ALLOWED");
        assertThat(snapshot(fixture)).isEqualTo(before);
    }

    /** 취소되지 않은 배송의 삭제는 기존 삭제 정책에 따라 거절한다. */
    @ParameterizedTest
    @EnumSource(value = DeliveryStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "CANCELLED")
    @DisplayName("주문 ID 삭제도 CANCELLED 이외 상태는 409이고 데이터를 유지한다")
    void rejectsDeletionBeforeCancellation(DeliveryStatus status) throws Exception {
        Fixture fixture = createDelivery();
        jdbc.update("UPDATE p_deliveries SET status = ? WHERE id = ?", status.name(), fixture.deliveryId());
        Snapshot before = snapshot(fixture);

        assertError(delete(fixture, REQUESTER), 409, "DELIVERY_DELETION_NOT_ALLOWED");
        assertThat(snapshot(fixture)).isEqualTo(before);
    }

    /** 배송 경로의 2구간 취소 실패 시 배송과 배송 경로의 1구간 변경까지 롤백한다. */
    @Test
    @DisplayName("주문 ID 취소 중 배송 경로의 2구간이 실패하면 전체 변경을 롤백한다")
    void rollsBackCancellationWhenLaterRouteFails() throws Exception {
        Fixture fixture = createDelivery();
        jdbc.update("UPDATE p_delivery_routes SET status = 'IN_TRANSIT' WHERE delivery_id = ? AND sequence = 2",
                fixture.deliveryId());
        Snapshot before = snapshot(fixture);

        assertError(cancel(fixture, REQUESTER), 409, "DELIVERY_ROUTE_CANCELLATION_NOT_ALLOWED");
        assertThat(snapshot(fixture)).isEqualTo(before);
    }

    /** 테스트 DB 제약으로 삭제 저장 실패를 유도하여 주문 ID API의 트랜잭션을 검증한다. */
    @Test
    @DisplayName("주문 ID 삭제의 배송 경로 저장 실패는 배송과 모든 배송 경로 변경을 롤백한다")
    void rollsBackDeletionWhenRouteSaveFails() throws Exception {
        Fixture fixture = createDelivery();
        assertCancellation(cancel(fixture, REQUESTER), fixture);
        Snapshot before = snapshot(fixture);
        jdbc.execute("ALTER TABLE p_delivery_routes ADD CONSTRAINT chk_order_delete_failure "
                + "CHECK (NOT (sequence = 2 AND deleted_at IS NOT NULL))");
        try {
            assertError(delete(fixture, REQUESTER), 500, "INTERNAL_SERVER_ERROR");
            assertThat(snapshot(fixture)).isEqualTo(before);
        } finally {
            jdbc.execute("ALTER TABLE p_delivery_routes DROP CONSTRAINT chk_order_delete_failure");
        }
    }

    /** 두 API의 헤더와 주문 UUID 검증이 배송 변경 전에 적용되는지 확인한다. */
    @ParameterizedTest
    @ValueSource(strings = {"POST", "DELETE"})
    @DisplayName("주문 ID API는 필수 헤더와 UUID 형식을 검증하며 배송을 변경하지 않는다")
    void validatesHeadersAndOrderId(String method) throws Exception {
        Fixture fixture = createDelivery();
        Snapshot before = snapshot(fixture);
        String path = orderPath(fixture.orderId().toString(), method);
        assertError(send(method, path, null, "USER", null), 401, "USER_ID_REQUIRED");
        assertError(send(method, path, " ", "USER", null), 401, "USER_ID_REQUIRED");
        assertError(send(method, path, "invalid-uuid", "USER", null), 400, "INVALID_USER_ID");
        assertError(send(method, path, REQUESTER.toString(), null, null), 401, "ROLE_REQUIRED");
        assertError(send(method, path, REQUESTER.toString(), " ", null), 401, "ROLE_REQUIRED");
        assertError(send(method, orderPath("invalid-uuid", method), REQUESTER.toString(), "USER", null),
                400, "INVALID_PARAMETER");
        assertThat(snapshot(fixture)).isEqualTo(before);
    }

    /** 배송이 없어도 취소와 삭제를 완료하고 재요청에서 최초 관리 기록을 유지한다. */
    @Test
    @DisplayName("배송 없는 취소와 삭제는 생성 차단을 유지하고 감사 기록을 덮어쓰지 않는다")
    void compensationWithoutDeliveryPreservesBlockAndAudit() throws Exception {
        UUID orderId = UUID.randomUUID();
        String cancelPath = orderPath(orderId.toString(), "POST");
        String deletePath = orderPath(orderId.toString(), "DELETE");

        assertCreationBlocked(send("POST", cancelPath, REQUESTER.toString(), "USER", null), orderId);
        Map<String, Object> cancelled = controlSnapshot(orderId);
        assertThat(cancelled.get("STATUS")).isEqualTo("CANCELLED");
        assertThat(cancelled.get("CREATED_BY")).isEqualTo(REQUESTER);
        assertThat(cancelled.get("UPDATED_BY")).isEqualTo(REQUESTER);

        assertCreationBlocked(send("POST", cancelPath, RETRY_REQUESTER.toString(), "USER", null), orderId);
        assertThat(controlSnapshot(orderId)).isEqualTo(cancelled);

        String body = creationBody(orderId, UUID.randomUUID(), UUID.randomUUID());
        assertError(send("POST", "/internal/deliveries", CREATOR.toString(), "USER", body),
                409, "DELIVERY_CREATION_BLOCKED");
        assertThat(controlSnapshot(orderId)).isEqualTo(cancelled);

        HttpResponse<String> deletion = send("DELETE", deletePath, REQUESTER.toString(), "USER", null);
        assertThat(deletion.statusCode()).isEqualTo(204);
        assertThat(deletion.body()).isEmpty();
        Map<String, Object> deleted = controlSnapshot(orderId);
        assertThat(deleted.get("STATUS")).isEqualTo("DELETED");
        assertThat(deleted.get("CREATED_AT")).isEqualTo(cancelled.get("CREATED_AT"));
        assertThat(deleted.get("DELETED_AT")).isNull();

        HttpResponse<String> retry = send("DELETE", deletePath, RETRY_REQUESTER.toString(), "USER", null);
        assertThat(retry.statusCode()).isEqualTo(204);
        assertThat(retry.body()).isEmpty();
        assertCreationBlocked(send("POST", cancelPath, RETRY_REQUESTER.toString(), "USER", null), orderId);
        assertError(send("POST", "/internal/deliveries", CREATOR.toString(), "USER", body),
                409, "DELIVERY_CREATION_BLOCKED");
        assertThat(controlSnapshot(orderId)).isEqualTo(deleted);
        assertThat(controlRepository.count()).isEqualTo(1);
        assertThat(deliveryRepository.count()).isZero();
        assertThat(deliveryRouteRepository.count()).isZero();
        org.mockito.Mockito.verifyNoInteractions(hubClient);
    }

    /** 허브 조회를 멈춘 동안 취소를 완료해 잠금 범위와 최종 저장 전 재검증을 확인한다. */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("허브 조회 중 취소 또는 삭제가 완료되면 늦은 생성은 409이며 배송이 남지 않는다")
    void blocksLateCreationDuringHubLookup(boolean deleteAfterCancel) throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID originId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        CountDownLatch hubEntered = new CountDownLatch(1);
        CountDownLatch releaseHub = new CountDownLatch(1);
        when(hubClient.getRoute(originId, destinationId)).thenAnswer(invocation -> {
            hubEntered.countDown();
            assertThat(releaseHub.await(15, TimeUnit.SECONDS)).isTrue();
            return hubResponse(originId, destinationId);
        });

        try (var executor = Executors.newSingleThreadExecutor()) {
            var creation = executor.submit(() -> send("POST", "/internal/deliveries", CREATOR.toString(),
                    "USER", creationBody(orderId, originId, destinationId)));
            try {
                assertThat(hubEntered.await(10, TimeUnit.SECONDS)).isTrue();
                // 허브 호출이 끝나지 않아도 취소 응답이 먼저 완료돼야 한다.
                assertCreationBlocked(send("POST", orderPath(orderId.toString(), "POST"),
                        REQUESTER.toString(), "USER", null), orderId);
                if (deleteAfterCancel) {
                    assertThat(send("DELETE", orderPath(orderId.toString(), "DELETE"),
                            REQUESTER.toString(), "USER", null).statusCode()).isEqualTo(204);
                }
            } finally {
                releaseHub.countDown();
            }
            assertError(creation.get(20, TimeUnit.SECONDS), 409, "DELIVERY_CREATION_BLOCKED");
        }

        assertThat(controlSnapshot(orderId).get("STATUS"))
                .isEqualTo(deleteAfterCancel ? "DELETED" : "CANCELLED");
        assertThat(deliveryRepository.count()).isZero();
        assertThat(deliveryRouteRepository.count()).isZero();
    }

    /** 두 생성 요청이 모두 사전 조회를 통과한 뒤 동시에 최종 저장하도록 허브 응답을 조율한다. */
    @Test
    @DisplayName("동일 주문 동시 생성은 201과 200을 반환하고 배송 하나와 배송 경로 두 개만 저장한다")
    void concurrentCreationReturnsSameDelivery() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID originId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        CyclicBarrier hubBarrier = new CyclicBarrier(2);
        when(hubClient.getRoute(originId, destinationId)).thenAnswer(invocation -> {
            hubBarrier.await(10, TimeUnit.SECONDS);
            return hubResponse(originId, destinationId);
        });
        String body = creationBody(orderId, originId, destinationId);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> send("POST", "/internal/deliveries", CREATOR.toString(), "USER", body));
            var second = executor.submit(() -> send("POST", "/internal/deliveries", REQUESTER.toString(), "USER", body));
            HttpResponse<String> firstResponse = first.get(20, TimeUnit.SECONDS);
            HttpResponse<String> secondResponse = second.get(20, TimeUnit.SECONDS);
            assertThat(List.of(firstResponse.statusCode(), secondResponse.statusCode()))
                    .containsExactlyInAnyOrder(201, 200);
            assertThat((String) JsonPath.read(firstResponse.body(), "$.deliveryId"))
                    .isEqualTo((String) JsonPath.read(secondResponse.body(), "$.deliveryId"));
        }

        assertThat(controlRepository.count()).isEqualTo(1);
        assertThat(controlSnapshot(orderId).get("STATUS")).isEqualTo("ALLOWED");
        assertThat(deliveryRepository.count()).isEqualTo(1);
        assertThat(deliveryRouteRepository.count()).isEqualTo(2);
    }

    /** 관리 기록도 없는 상태에서 여러 취소 요청을 동시에 시작한다. */
    @Test
    @DisplayName("최초 취소 동시 요청은 관리 기록 하나만 만들고 모두 생성 차단 성공을 반환한다")
    void concurrentInitialCancellationsShareOneControl() throws Exception {
        UUID orderId = UUID.randomUUID();
        int requests = 8;
        CyclicBarrier start = new CyclicBarrier(requests);
        try (var executor = Executors.newFixedThreadPool(requests)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<HttpResponse<String>>>();
            for (int i = 0; i < requests; i++) {
                UUID requester = UUID.randomUUID();
                futures.add(executor.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return send("POST", orderPath(orderId.toString(), "POST"), requester.toString(), "USER", null);
                }));
            }
            for (var future : futures) {
                assertCreationBlocked(future.get(20, TimeUnit.SECONDS), orderId);
            }
        }
        assertThat(controlRepository.count()).isEqualTo(1);
        assertThat(controlSnapshot(orderId).get("STATUS")).isEqualTo("CANCELLED");
        assertThat(deliveryRepository.count()).isZero();
        assertThat(deliveryRouteRepository.count()).isZero();
    }

    /** 생성과 취소를 동시에 시작하고 어느 순서라도 활성 배송이 남지 않는지 확인한다. */
    @Test
    @DisplayName("최초 생성과 취소가 경합해도 배송은 취소되거나 생성 차단되며 WAITING으로 남지 않는다")
    void concurrentCreationAndCancellationLeaveNoActiveDelivery() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID hubId = UUID.randomUUID();
        CyclicBarrier start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var creation = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return send("POST", "/internal/deliveries", CREATOR.toString(), "USER",
                        creationBody(orderId, hubId, hubId));
            });
            var cancellation = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return send("POST", orderPath(orderId.toString(), "POST"), REQUESTER.toString(), "USER", null);
            });
            HttpResponse<String> created = creation.get(20, TimeUnit.SECONDS);
            HttpResponse<String> cancelled = cancellation.get(20, TimeUnit.SECONDS);
            assertThat(controlSnapshot(orderId).get("STATUS")).isEqualTo("CANCELLED");
            if (created.statusCode() == 201) {
                UUID deliveryId = UUID.fromString(JsonPath.read(created.body(), "$.deliveryId"));
                Fixture fixture = new Fixture(orderId, deliveryId);
                assertCancellation(cancelled, fixture);
                assertThat(snapshot(fixture).delivery().get("STATUS")).isEqualTo("CANCELLED");
                assertThat(snapshot(fixture).routes()).hasSize(1)
                        .allSatisfy(route -> assertThat(route.get("STATUS")).isEqualTo("CANCELLED"));
            } else {
                assertError(created, 409, "DELIVERY_CREATION_BLOCKED");
                assertCreationBlocked(cancelled, orderId);
                assertThat(deliveryRepository.count()).isZero();
                assertThat(deliveryRouteRepository.count()).isZero();
            }
        }
        assertThat(controlRepository.count()).isEqualTo(1);
    }

    /** 기존 배송 ID API의 응답을 유지하면서 관리 기록의 생성 차단까지 반영한다. */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("기존 배송 ID 취소와 삭제도 관리 기록을 반영하고 같은 주문의 재생성을 막는다")
    void legacyApisAlsoBlockCreation(boolean deleteAfterCancel) throws Exception {
        Fixture fixture = createDelivery();
        Snapshot before = snapshot(fixture);
        // 관리 테이블 도입 이전에 생성된 배송도 처리할 수 있어야 한다.
        controlRepository.deleteAllInBatch();
        HttpResponse<String> cancel = send("POST", "/internal/deliveries/" + fixture.deliveryId() + "/cancel",
                REQUESTER.toString(), "USER", null);
        assertThat(cancel.statusCode()).isEqualTo(200);
        assertThat((String) JsonPath.read(cancel.body(), "$.status")).isEqualTo("CANCELLED");
        assertThat(controlSnapshot(fixture.orderId()).get("STATUS")).isEqualTo("CANCELLED");
        if (deleteAfterCancel) {
            assertThat(send("DELETE", "/internal/deliveries/" + fixture.deliveryId(),
                    REQUESTER.toString(), "USER", null).statusCode()).isEqualTo(204);
            assertThat(controlSnapshot(fixture.orderId()).get("STATUS")).isEqualTo("DELETED");
        }
        String body = creationBody(fixture.orderId(), (UUID) before.delivery().get("DEPARTURE_HUB_ID"),
                (UUID) before.delivery().get("DESTINATION_HUB_ID"));
        assertError(send("POST", "/internal/deliveries", CREATOR.toString(), "USER", body), 409,
                deleteAfterCancel ? "DELETED_DELIVERY_RECREATION_NOT_ALLOWED" : "DELIVERY_CREATION_BLOCKED");
    }

    /** 허브 API를 가짜 응답으로 대체하고 생성 API로 두 배송 경로가 있는 배송을 준비한다. */
    private Fixture createDelivery() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID originId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        when(hubClient.getRoute(originId, destinationId)).thenReturn(hubResponse(originId, destinationId));
        String body = creationBody(orderId, originId, destinationId);
        HttpResponse<String> response = send("POST", "/internal/deliveries", CREATOR.toString(), "USER", body);
        assertThat(response.statusCode()).isEqualTo(201);
        Fixture fixture = new Fixture(orderId, UUID.fromString(JsonPath.read(response.body(), "$.deliveryId")));
        assertThat(snapshot(fixture).routes()).hasSize(2);
        return fixture;
    }

    /** 주문 ID 취소 요청을 보낸다. */
    private HttpResponse<String> cancel(Fixture fixture, UUID requester) throws Exception {
        return send("POST", orderPath(fixture.orderId().toString(), "POST"), requester.toString(), "USER", null);
    }

    /** 주문 ID 삭제 요청을 보낸다. */
    private HttpResponse<String> delete(Fixture fixture, UUID requester) throws Exception {
        return send("DELETE", orderPath(fixture.orderId().toString(), "DELETE"), requester.toString(), "USER", null);
    }

    /** HTTP 메서드에 맞는 주문 ID 취소 또는 삭제 URL을 반환한다. */
    private String orderPath(String orderId, String method) {
        return "/internal/deliveries/orders/" + orderId + ("POST".equals(method) ? "/cancel" : "");
    }

    /** 지정된 헤더만 포함하여 실제 HTTP 요청을 보내고 UTF-8 응답을 반환한다. */
    private HttpResponse<String> send(String method, String path, String userId, String role, String body)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(20));
        if (userId != null) builder.header("X-User-Id", userId);
        if (role != null) builder.header("X-Role-Name", role);
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** HTTP 요청 완료 후 DB의 배송과 정렬된 배송 경로 전체 값을 조회한다. */
    private Snapshot snapshot(Fixture fixture) {
        return new Snapshot(jdbc.queryForMap("SELECT * FROM p_deliveries WHERE id = ?", fixture.deliveryId()),
                jdbc.queryForList("SELECT * FROM p_delivery_routes WHERE delivery_id = ? ORDER BY sequence",
                        fixture.deliveryId()), controlSnapshot(fixture.orderId()));
    }

    /** 주문별 관리 기록의 상태와 감사 필드를 조회한다. */
    private Map<String, Object> controlSnapshot(UUID orderId) {
        return jdbc.queryForMap("SELECT * FROM p_delivery_order_controls WHERE order_id = ?", orderId);
    }

    /** 생성 차단 응답에 주문 ID와 null 배송 ID가 명시되는지 검증한다. */
    private void assertCreationBlocked(HttpResponse<String> response, UUID orderId) {
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat((String) JsonPath.read(response.body(), "$.orderId")).isEqualTo(orderId.toString());
        assertThat((Object) JsonPath.read(response.body(), "$.deliveryId")).isNull();
        assertThat((String) JsonPath.read(response.body(), "$.result")).isEqualTo("CREATION_BLOCKED");
    }

    /** 테스트 생성 요청을 JSON으로 구성한다. */
    private String creationBody(UUID orderId, UUID originId, UUID destinationId) {
        return """
                {"orderId":"%s","departureHubId":"%s","destinationHubId":"%s",
                 "address":"Test address","recipientName":"Test recipient","recipientPhone":"010-1234-5678"}
                """.formatted(orderId, originId, destinationId);
    }

    /** 실제 허브 API 대신 사용할 이동 정보를 구성한다. */
    private HubRouteResponse hubResponse(UUID originId, UUID destinationId) {
        return new HubRouteResponse(destinationId, "Destination", new BigDecimal("100.5"), 90,
                UUID.randomUUID(), originId, "Origin");
    }

    /** 주문 ID 기준 취소 응답의 주문 ID, 배송 ID, 처리 결과를 검증한다. */
    private void assertCancellation(HttpResponse<String> response, Fixture fixture) {
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat((String) JsonPath.read(response.body(), "$.deliveryId")).isEqualTo(fixture.deliveryId().toString());
        assertThat((String) JsonPath.read(response.body(), "$.orderId")).isEqualTo(fixture.orderId().toString());
        assertThat((String) JsonPath.read(response.body(), "$.result")).isEqualTo("CANCELLED");
    }

    /** HTTP 상태와 공통 오류 코드를 함께 검증한다. */
    private void assertError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo(code);
    }

    /** 취소 상태와 감사 사용자 UUID를 검증하고 생성 기록 보존을 확인한다. */
    private void assertCancelled(Map<String, Object> before, Map<String, Object> after) {
        assertThat(after.get("STATUS")).isEqualTo("CANCELLED");
        assertThat(after.get("UPDATED_BY")).isEqualTo(REQUESTER);
        assertThat(after.get("UPDATED_AT")).isNotNull();
        assertThat(after.get("CREATED_BY")).isEqualTo(before.get("CREATED_BY"));
        assertThat(after.get("CREATED_AT")).isEqualTo(before.get("CREATED_AT"));
        assertThat(after.get("DELETED_AT")).isNull();
        assertThat(after.get("DELETED_BY")).isNull();
    }

    /** 배송과 모든 배송 경로의 삭제 시각, 삭제자, 생성 기록 보존을 검증한다. */
    private void assertDeleted(Map<String, Object> before, Map<String, Object> after, Object deletedAt) {
        assertThat(after.get("STATUS")).isEqualTo("CANCELLED");
        assertThat(after.get("DELETED_AT")).isEqualTo(deletedAt);
        assertThat(after.get("DELETED_BY")).isEqualTo(REQUESTER);
        assertThat(after.get("CREATED_BY")).isEqualTo(before.get("CREATED_BY"));
        assertThat(after.get("CREATED_AT")).isEqualTo(before.get("CREATED_AT"));
    }

    private record Fixture(UUID orderId, UUID deliveryId) { }
    private record Snapshot(Map<String, Object> delivery, List<Map<String, Object>> routes,
                            Map<String, Object> control) { }
}

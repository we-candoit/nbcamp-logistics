package logistics.order.presentation.controller;

import feign.Request;
import feign.RetryableException;
import logistics.order.application.service.OrderService;
import logistics.order.common.AuthHeaders;
import logistics.order.presentation.dto.request.CreateOrderRequest;
import logistics.order.domain.enums.OrderStatus;
import logistics.order.presentation.dto.response.CancelOrderResponse;
import logistics.order.presentation.dto.response.CreateOrderResponse;
import logistics.order.presentation.dto.response.OrderDetailResponse;
import logistics.order.presentation.dto.response.OrderListResponse;
import logistics.order.common.exception.CustomException;
import logistics.order.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

//Web 계층만 띄워서 AuthInterceptor → @Valid → Controller → GlobalExceptionHandler 흐름 확인
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    private final UUID userId = UUID.randomUUID();
    private final UUID requesterId = UUID.randomUUID();
    private final UUID supplierId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID deliveryId = UUID.randomUUID();

    //정상 요청 바디 / 테스트마다 필드를 바꿔서 사용
    private Map<String, Object> validBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requesterId", requesterId.toString());
        body.put("supplierId", supplierId.toString());
        body.put("itemId", itemId.toString());
        body.put("price", 15000);
        body.put("quantity", 3);
        body.put("requestNote", "문 앞에 놓아주세요");
        body.put("recipientName", "홍길동");
        body.put("recipientPhone", "010-1234-5678");
        return body;
    }

    private static String toJson(Map<String, Object> body) {
        return body.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":" + toJsonValue(e.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String toJsonValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Number) return value.toString();
        return "\"" + value + "\"";
    }

    //인증 헤더가 모두 있는 주문 생성 요청
    private MockHttpServletRequestBuilder createOrder(Map<String, Object> body) {
        return post("/api/orders")
                .header(AuthHeaders.USER_ID, userId.toString())
                .header(AuthHeaders.ROLE, "MASTER")
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8)
                .content(toJson(body));
    }

    @Nested
    @DisplayName("정상 케이스")
    class Success {

        @BeforeEach
        void setUp() {
            given(orderService.create(any(CreateOrderRequest.class)))
                    .willReturn(new CreateOrderResponse(orderId, itemId, deliveryId, LocalDateTime.of(2026, 10, 18, 12, 0)));
        }

        @Test
        public void 정상_요청이면_201과_주문정보를_반환한다() throws Exception {
            //when, then
            mockMvc.perform(createOrder(validBody()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                    .andExpect(jsonPath("$.itemId").value(itemId.toString()))
                    .andExpect(jsonPath("$.deliveryId").value(deliveryId.toString()))
                    .andExpect(jsonPath("$.createdAt").exists());
        }

        @Test
        public void 요청바디가_CreateOrderRequest로_변환되어_서비스에_전달된다() throws Exception {
            //given
            ArgumentCaptor<CreateOrderRequest> captor = ArgumentCaptor.forClass(CreateOrderRequest.class);

            //when
            mockMvc.perform(createOrder(validBody()))
                    .andExpect(status().isCreated());

            //then
            verify(orderService).create(captor.capture());
            CreateOrderRequest request = captor.getValue();

            assertThat(request.getRequesterId()).isEqualTo(requesterId);
            assertThat(request.getSupplierId()).isEqualTo(supplierId);
            assertThat(request.getItemId()).isEqualTo(itemId);
            assertThat(request.getPrice()).isEqualTo(15000L);
            assertThat(request.getQuantity()).isEqualTo(3L);
            assertThat(request.getRequestNote()).isEqualTo("문 앞에 놓아주세요");
            assertThat(request.getRecipientName()).isEqualTo("홍길동");
            assertThat(request.getRecipientPhone()).isEqualTo("010-1234-5678");
        }

        @Test
        public void 요청사항이_없어도_201을_반환한다() throws Exception {
            //given
            Map<String, Object> body = validBody();
            body.remove("requestNote");

            //when, then
            mockMvc.perform(createOrder(body))
                    .andExpect(status().isCreated());
        }

        @Test
        public void 수량_경계값_1과_100은_허용된다() throws Exception {
            //given
            Map<String, Object> min = validBody();
            min.put("quantity", 1);
            Map<String, Object> max = validBody();
            max.put("quantity", 100);

            //when, then
            mockMvc.perform(createOrder(min)).andExpect(status().isCreated());
            mockMvc.perform(createOrder(max)).andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class Failure {

        @Test
        public void UserId_헤더가_없으면_401을_반환하고_서비스를_호출하지_않는다() throws Exception {
            //when, then
            mockMvc.perform(post("/api/orders")
                            .header(AuthHeaders.ROLE, "MASTER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(validBody())))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401));

            verify(orderService, never()).create(any());
        }

        @Test
        public void Role_헤더가_없으면_401을_반환하고_서비스를_호출하지_않는다() throws Exception {
            //when, then
            mockMvc.perform(post("/api/orders")
                            .header(AuthHeaders.USER_ID, userId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(validBody())))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401));

            verify(orderService, never()).create(any());
        }

        static Stream<Arguments> invalidBodies() {
            return Stream.of(
                    Arguments.of("요청업체ID 누락", "requesterId", null),
                    Arguments.of("공급업체ID 누락", "supplierId", null),
                    Arguments.of("상품ID 누락", "itemId", null),
                    Arguments.of("가격 누락", "price", null),
                    Arguments.of("가격 음수", "price", -1),
                    Arguments.of("가격 10억 초과", "price", 1_000_000_001),
                    Arguments.of("수량 누락", "quantity", null),
                    Arguments.of("수량 0", "quantity", 0),
                    Arguments.of("수량 101", "quantity", 101),
                    Arguments.of("요청사항 101자", "requestNote", "가".repeat(101)),
                    Arguments.of("수령인 이름 공백", "recipientName", " "),
                    Arguments.of("수령인 이름 51자", "recipientName", "가".repeat(51)),
                    Arguments.of("수령인 전화번호 누락", "recipientPhone", null),
                    Arguments.of("수령인 전화번호 21자", "recipientPhone", "0".repeat(21))
            );
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidBodies")
        public void 요청값_검증에_실패하면_400을_반환하고_서비스를_호출하지_않는다(
                String description, String field, Object value) throws Exception {
            //given
            Map<String, Object> body = validBody();
            body.put(field, value);

            //when, then
            mockMvc.perform(createOrder(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value(startsWith(field + ": ")))
                    .andExpect(jsonPath("$.timestamp").exists());

            verify(orderService, never()).create(any());
        }

        static Stream<Arguments> unreadableBodies() {
            return Stream.of(
                    Arguments.of("UUID 형식이 아닌 요청업체ID", "{\"requesterId\":\"abc\"}"),
                    Arguments.of("숫자가 아닌 수량", "{\"quantity\":\"세개\"}"),
                    Arguments.of("JSON 문법 오류", "{\"requesterId\":"),
                    Arguments.of("빈 본문", "")
            );
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("unreadableBodies")
        public void 요청본문을_변환할_수_없으면_400과_정해진_메시지를_반환하고_서비스를_호출하지_않는다(
                String description, String content) throws Exception {
            //when, then
            mockMvc.perform(post("/api/orders")
                            .header(AuthHeaders.USER_ID, userId.toString())
                            .header(AuthHeaders.ROLE, "MASTER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .characterEncoding(StandardCharsets.UTF_8)
                            .content(content))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."))
                    .andExpect(jsonPath("$.timestamp").exists());

            verify(orderService, never()).create(any());
        }

        @Test
        public void 요청값_검증_메시지는_DTO에_정의한_메시지로_응답한다() throws Exception {
            //given
            Map<String, Object> body = validBody();
            body.put("quantity", 0);

            //when, then
            mockMvc.perform(createOrder(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("quantity: 상품 수량은 1개 이상이어야 합니다."));
        }

        @Test
        public void 여러_필드가_틀리면_모든_필드의_메시지를_응답한다() throws Exception {
            //given
            Map<String, Object> body = validBody();
            body.put("quantity", 0);
            body.put("recipientName", " ");

            //when, then
            mockMvc.perform(createOrder(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("quantity: ")))
                    .andExpect(jsonPath("$.message").value(containsString("recipientName: ")));
        }

        @Test
        public void 서비스에서_CustomException이_발생하면_ErrorCode의_상태코드로_응답한다() throws Exception {
            //given
            given(orderService.create(any())).willThrow(new CustomException(ErrorCode.C409_ORDER_PRICE_CHANGED));

            //when, then
            mockMvc.perform(createOrder(validBody()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        public void 예상하지_못한_예외가_발생하면_500과_정해진_문구를_반환한다() throws Exception {
            //given
            given(orderService.create(any())).willThrow(new IllegalStateException("could not execute statement"));

            //when, then
            mockMvc.perform(createOrder(validBody()))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));
        }

        @Test
        public void 지원하지_않는_HTTP_메서드면_405를_반환한다() throws Exception {
            //when, then
            mockMvc.perform(put("/api/orders")
                            .header(AuthHeaders.USER_ID, userId.toString())
                            .header(AuthHeaders.ROLE, "MASTER"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.status").value(405));
        }

        @Test
        public void 외부서비스가_응답하지_않으면_503을_반환한다() throws Exception {
            //given
            Request request = Request.create(Request.HttpMethod.GET, "http://mock",
                    Map.of(), null, StandardCharsets.UTF_8, null);
            given(orderService.create(any()))
                    .willThrow(new RetryableException(-1, "Read timed out", Request.HttpMethod.GET, (Long) null, request));

            //when, then
            mockMvc.perform(createOrder(validBody()))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value(503))
                    .andExpect(jsonPath("$.message").value("외부 서비스에 연결할 수 없습니다."));
        }
    }

    @Nested
    @DisplayName("주문 단건 조회")
    class GetOrder {

        private OrderDetailResponse response() {
            return new OrderDetailResponse(orderId, deliveryId, userId, requesterId, OrderStatus.CONFIRMED,
                    "서울시 강남구", "문 앞에 놓아주세요",
                    new OrderDetailResponse.Item(itemId, supplierId, "생수 2L", 15000L, 3L, 45000L),
                    LocalDateTime.of(2026, 10, 18, 12, 0));
        }

        @Test
        public void 정상_요청이면_200과_주문정보를_반환한다() throws Exception {
            //given
            given(orderService.getOrderById(orderId)).willReturn(response());

            //when, then
            mockMvc.perform(get("/api/orders/{orderId}", orderId)
                            .header(AuthHeaders.USER_ID, userId.toString())
                            .header(AuthHeaders.ROLE, "MASTER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                    .andExpect(jsonPath("$.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.deliveryId").value(deliveryId.toString()))
                    .andExpect(jsonPath("$.orderUserId").value(userId.toString()))
                    .andExpect(jsonPath("$.requesterId").value(requesterId.toString()))
                    .andExpect(jsonPath("$.address").value("서울시 강남구"))
                    .andExpect(jsonPath("$.requestNote").value("문 앞에 놓아주세요"))
                    .andExpect(jsonPath("$.createdAt").exists())
                    .andExpect(jsonPath("$.item.itemId").value(itemId.toString()))
                    .andExpect(jsonPath("$.item.supplierId").value(supplierId.toString()))
                    .andExpect(jsonPath("$.item.itemName").value("생수 2L"))
                    .andExpect(jsonPath("$.item.price").value(15000))
                    .andExpect(jsonPath("$.item.quantity").value(3))
                    .andExpect(jsonPath("$.item.totalPrice").value(45000));
        }

        @Test
        public void 없는_주문이면_404를_반환한다() throws Exception {
            //given
            given(orderService.getOrderById(orderId)).willThrow(new CustomException(ErrorCode.C404_ORDER_NOT_FOUND));

            //when, then
            mockMvc.perform(get("/api/orders/{orderId}", orderId)
                            .header(AuthHeaders.USER_ID, userId.toString())
                            .header(AuthHeaders.ROLE, "MASTER"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        public void 주문ID가_UUID_형식이_아니면_400을_반환하고_서비스를_호출하지_않는다() throws Exception {
            //when, then
            mockMvc.perform(get("/api/orders/{orderId}", "abc")
                            .header(AuthHeaders.USER_ID, userId.toString())
                            .header(AuthHeaders.ROLE, "MASTER"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("orderId: 형식이 올바르지 않습니다."))
                    .andExpect(jsonPath("$.timestamp").exists());

            verify(orderService, never()).getOrderById(any());
        }

        @Test
        public void 인증_헤더가_없으면_401을_반환한다() throws Exception {
            //when, then
            mockMvc.perform(get("/api/orders/{orderId}", orderId))
                    .andExpect(status().isUnauthorized());

            verify(orderService, never()).getOrderById(any());
        }
    }

    @Nested
    @DisplayName("주문 취소")
    class CancelOrder {

        private MockHttpServletRequestBuilder cancel(Object orderId) {
            return post("/api/orders/{orderId}/cancel", orderId)
                    .header(AuthHeaders.USER_ID, userId.toString())
                    .header(AuthHeaders.ROLE, "MASTER");
        }

        @Test
        public void 정상_요청이면_200과_취소된_주문정보를_반환한다() throws Exception {
            //given
            given(orderService.cancelOrderById(orderId))
                    .willReturn(new CancelOrderResponse(orderId, deliveryId, OrderStatus.CANCELLED));

            //when, then
            mockMvc.perform(cancel(orderId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                    .andExpect(jsonPath("$.deliveryId").value(deliveryId.toString()))
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
        }

        @Test
        public void 배송이_취소되지_않으면_409를_반환한다() throws Exception {
            //given
            given(orderService.cancelOrderById(orderId))
                    .willThrow(new CustomException(ErrorCode.C409_ORDER_NOT_CANCELABLE));

            //when, then
            mockMvc.perform(cancel(orderId))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));
        }

        @Test
        public void 다른_사람의_주문이면_403을_반환한다() throws Exception {
            //given
            given(orderService.cancelOrderById(orderId))
                    .willThrow(new CustomException(ErrorCode.C403_FORBIDDEN_ORDER));

            //when, then
            mockMvc.perform(cancel(orderId))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }

        @Test
        public void 없는_주문이면_404를_반환한다() throws Exception {
            //given
            given(orderService.cancelOrderById(orderId))
                    .willThrow(new CustomException(ErrorCode.C404_ORDER_NOT_FOUND));

            //when, then
            mockMvc.perform(cancel(orderId))
                    .andExpect(status().isNotFound());
        }

        @Test
        public void 주문ID가_UUID_형식이_아니면_400을_반환하고_서비스를_호출하지_않는다() throws Exception {
            //when, then
            mockMvc.perform(cancel("abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("orderId: 형식이 올바르지 않습니다."));

            verify(orderService, never()).cancelOrderById(any());
        }

        @Test
        public void 인증_헤더가_없으면_401을_반환한다() throws Exception {
            //when, then
            mockMvc.perform(post("/api/orders/{orderId}/cancel", orderId))
                    .andExpect(status().isUnauthorized());

            verify(orderService, never()).cancelOrderById(any());
        }
    }

    @Nested
    @DisplayName("주문 삭제")
    class DeleteOrder {

        private MockHttpServletRequestBuilder deleteRequest(Object orderId) {
            return delete("/api/orders/{orderId}", orderId)
                    .header(AuthHeaders.USER_ID, userId.toString())
                    .header(AuthHeaders.ROLE, "MASTER");
        }

        @Test
        public void 정상_요청이면_204를_반환한다() throws Exception {
            //when, then
            mockMvc.perform(deleteRequest(orderId))
                    .andExpect(status().isNoContent());

            verify(orderService).deleteOrderById(orderId);
        }

        @Test
        public void 배송이_삭제되지_않으면_409를_반환한다() throws Exception {
            //given
            willThrow(new CustomException(ErrorCode.C409_ORDER_NOT_DELETABLE))
                    .given(orderService).deleteOrderById(orderId);

            //when, then
            mockMvc.perform(deleteRequest(orderId))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));
        }

        @Test
        public void 취소되지_않은_주문이면_409를_반환한다() throws Exception {
            //given
            willThrow(new CustomException(ErrorCode.C409_INVALID_ORDER_STATUS))
                    .given(orderService).deleteOrderById(orderId);

            //when, then
            mockMvc.perform(deleteRequest(orderId))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));
        }

        @Test
        public void 다른_사람의_주문이면_403을_반환한다() throws Exception {
            //given
            willThrow(new CustomException(ErrorCode.C403_FORBIDDEN_ORDER))
                    .given(orderService).deleteOrderById(orderId);

            //when, then
            mockMvc.perform(deleteRequest(orderId))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }

        @Test
        public void 없는_주문이면_404를_반환한다() throws Exception {
            //given
            willThrow(new CustomException(ErrorCode.C404_ORDER_NOT_FOUND))
                    .given(orderService).deleteOrderById(orderId);

            //when, then
            mockMvc.perform(deleteRequest(orderId))
                    .andExpect(status().isNotFound());
        }

        @Test
        public void 주문ID가_UUID_형식이_아니면_400을_반환하고_서비스를_호출하지_않는다() throws Exception {
            //when, then
            mockMvc.perform(deleteRequest("abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("orderId: 형식이 올바르지 않습니다."));

            verify(orderService, never()).deleteOrderById(any());
        }

        @Test
        public void 인증_헤더가_없으면_401을_반환한다() throws Exception {
            //when, then
            mockMvc.perform(delete("/api/orders/{orderId}", orderId))
                    .andExpect(status().isUnauthorized());

            verify(orderService, never()).deleteOrderById(any());
        }
    }

    @Nested
    @DisplayName("주문 목록 조회")
    class GetOrderByStatus {

        private OrderListResponse response() {
            return new OrderListResponse(List.of(new OrderDetailResponse(orderId, deliveryId, userId, requesterId,
                    OrderStatus.CONFIRMED, "서울시 강남구", null,
                    new OrderDetailResponse.Item(itemId, supplierId, "생수 2L", 15000L, 3L, 45000L),
                    LocalDateTime.of(2026, 10, 19, 12, 0))));
        }

        @Test
        public void 상태_없이_요청하면_200과_주문목록을_반환한다() throws Exception {
            //given
            given(orderService.getOrdersByStatus(null)).willReturn(response());

            //when, then
            mockMvc.perform(get("/api/orders")
                            .header(AuthHeaders.USER_ID, userId.toString())
                            .header(AuthHeaders.ROLE, "MASTER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orders.length()").value(1))
                    .andExpect(jsonPath("$.orders[0].orderId").value(orderId.toString()))
                    .andExpect(jsonPath("$.orders[0].status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.orders[0].item.itemId").value(itemId.toString()));
        }

        @Test
        public void 상태를_지정하면_서비스에_그대로_전달한다() throws Exception {
            //given
            given(orderService.getOrdersByStatus(OrderStatus.CONFIRMED)).willReturn(new OrderListResponse(List.of()));

            //when, then
            mockMvc.perform(get("/api/orders").param("status", "CONFIRMED")
                            .header(AuthHeaders.USER_ID, userId.toString())
                            .header(AuthHeaders.ROLE, "MASTER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orders").isEmpty());

            verify(orderService).getOrdersByStatus(OrderStatus.CONFIRMED);
        }

        @Test
        public void 상태가_빈_값이면_상태_없이_전체를_조회한다() throws Exception {
            //given
            given(orderService.getOrdersByStatus(null)).willReturn(new OrderListResponse(List.of()));

            //when, then
            mockMvc.perform(get("/api/orders").param("status", "")
                            .header(AuthHeaders.USER_ID, userId.toString())
                            .header(AuthHeaders.ROLE, "MASTER"))
                    .andExpect(status().isOk());

            verify(orderService).getOrdersByStatus(null);
        }

        //enum 변환은 Spring이 처리 / 존재하지 않거나 대소문자가 다르면 변환 실패로 400
        @ParameterizedTest
        @ValueSource(strings = {"SHIPPING", "confirmed", "CANCELED"})
        public void 유효하지_않은_상태면_400을_반환하고_서비스를_호출하지_않는다(String status) throws Exception {
            //when, then
            mockMvc.perform(get("/api/orders").param("status", status)
                            .header(AuthHeaders.USER_ID, userId.toString())
                            .header(AuthHeaders.ROLE, "MASTER"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("status: 형식이 올바르지 않습니다."));

            verify(orderService, never()).getOrdersByStatus(any());
        }

        @Test
        public void 인증_헤더가_없으면_401을_반환한다() throws Exception {
            //when, then
            mockMvc.perform(get("/api/orders"))
                    .andExpect(status().isUnauthorized());

            verify(orderService, never()).getOrdersByStatus(any());
        }
    }
}

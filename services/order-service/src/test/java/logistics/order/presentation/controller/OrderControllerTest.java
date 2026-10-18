package logistics.order.presentation.controller;

import feign.Request;
import feign.RetryableException;
import logistics.order.application.service.OrderService;
import logistics.order.common.AuthHeaders;
import logistics.order.presentation.dto.request.CreateOrderRequest;
import logistics.order.presentation.dto.response.CreateOrderResponse;
import logistics.order.presentation.exception.CustomException;
import logistics.order.presentation.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}

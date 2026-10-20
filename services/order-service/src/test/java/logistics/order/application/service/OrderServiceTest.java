package logistics.order.application.service;

import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import logistics.order.application.compensation.OrderCompensator;
import logistics.order.application.validator.OrderValidator;
import logistics.order.common.AuthHeaders;
import logistics.order.domain.entity.Order;
import logistics.order.domain.entity.OrderItem;
import logistics.order.domain.enums.OrderStatus;
import logistics.order.infrastructure.client.CompanyServiceClient;
import logistics.order.infrastructure.client.DeliveryServiceClient;
import logistics.order.infrastructure.client.HubServiceClient;
import logistics.order.infrastructure.client.UserServiceClient;
import logistics.order.infrastructure.client.dto.CancelDeliveryResponse;
import logistics.order.infrastructure.client.dto.CreateDeliveryRequest;
import logistics.order.infrastructure.client.dto.CreateDeliveryResponse;
import logistics.order.infrastructure.client.dto.FetchCompanyByIdResponse;
import logistics.order.infrastructure.client.dto.FetchItemByIdResponse;
import logistics.order.infrastructure.client.dto.FetchUserByIdResponse;
import logistics.order.presentation.dto.request.CreateOrderRequest;
import logistics.order.presentation.dto.response.CancelOrderResponse;
import logistics.order.presentation.dto.response.CreateOrderResponse;
import logistics.order.presentation.dto.response.OrderDetailResponse;
import logistics.order.presentation.dto.response.OrderListResponse;
import logistics.order.common.exception.CustomException;
import logistics.order.common.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class OrderServiceTest {

    //외부 API는 아직 미구현 → Feign 클라이언트를 Mock으로 대체
    private final CompanyServiceClient companyServiceClient = mock(CompanyServiceClient.class);
    private final HubServiceClient hubServiceClient = mock(HubServiceClient.class);
    private final DeliveryServiceClient deliveryServiceClient = mock(DeliveryServiceClient.class);
    private final UserServiceClient userServiceClient = mock(UserServiceClient.class);
    private final OrderTransactionService orderTransactionService = mock(OrderTransactionService.class);

    //검증 로직은 실제 객체 사용 (목데이터로 실제 검증 흐름까지 확인)
    private final OrderValidator orderValidator = new OrderValidator();

    private final ClientService clientService = new ClientService(
            companyServiceClient, hubServiceClient, deliveryServiceClient, userServiceClient, orderValidator);

    //보상 로직도 실제 객체 사용 (보상 호출 흐름까지 확인)
    private final OrderCompensator orderCompensator =
            new OrderCompensator(clientService, orderTransactionService);

    private final OrderService orderService =
            new OrderService(orderValidator, orderTransactionService, clientService, orderCompensator);

    //===== 목데이터 =====
    private final UUID userId = UUID.randomUUID();
    private final UUID requesterId = UUID.randomUUID();
    private final UUID requesterHubId = UUID.randomUUID();
    private final UUID supplierId = UUID.randomUUID();
    private final UUID supplierHubId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();
    private final UUID deliveryId = UUID.randomUUID();
    private final Long price = 15_000L;
    private final Long quantity = 3L;

    private MockHttpServletRequest httpRequest;

    @BeforeEach
    void setUp() {
        //AuthInterceptor가 하는 일을 대신 수행 (헤더 값은 String으로 저장됨)
        httpRequest = new MockHttpServletRequest();
        httpRequest.setAttribute(AuthHeaders.USER_ID, userId.toString());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(httpRequest));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void loginAs(String role) {
        httpRequest.setAttribute(AuthHeaders.ROLE, role);
    }

    private CreateOrderRequest orderRequest() {
        return new CreateOrderRequest(requesterId, supplierId, itemId, price, quantity,
                "문 앞에 놓아주세요", "홍길동", "010-1234-5678");
    }

    private FetchItemByIdResponse itemResponse() {
        FetchItemByIdResponse item = new FetchItemByIdResponse();
        item.setItemId(itemId);
        item.setCompanyId(supplierId);
        item.setCompanyName("공급업체");
        item.setName("생수 2L");
        item.setPrice(price);
        item.setTotalQuantity(1_000L);
        return item;
    }

    private FetchCompanyByIdResponse companyResponse(UUID companyId, UUID hubId, String name, String address) {
        return new FetchCompanyByIdResponse(companyId, hubId, UUID.randomUUID(), name, address,
                LocalDateTime.now(), LocalDateTime.now());
    }

    //외부 API가 모두 정상 응답하는 상황
    private void givenExternalApisSucceed() {
        //MASTER도 유저 레코드가 있어 항상 조회 (이후 Slack ID 사용 예정)
        given(userServiceClient.fetchUserById(userId)).willReturn(new FetchUserByIdResponse(requesterId));
        given(companyServiceClient.fetchItemById(itemId)).willReturn(itemResponse());
        given(companyServiceClient.fetchCompanyById(requesterId))
                .willReturn(companyResponse(requesterId, requesterHubId, "요청업체", "서울시 강남구 테헤란로 1"));
        given(companyServiceClient.fetchCompanyById(supplierId))
                .willReturn(companyResponse(supplierId, supplierHubId, "공급업체", "부산시 해운대구 1"));
        given(hubServiceClient.decreaseStock(supplierHubId, itemId, quantity)).willReturn("OK");
        given(deliveryServiceClient.createDelivery(any(CreateDeliveryRequest.class)))
                .willAnswer(invocation -> {
                    CreateDeliveryRequest req = invocation.getArgument(0);
                    return new CreateDeliveryResponse(deliveryId, req.getOrderId(), "WAITING");
                });
    }

    private Request feignRequest() {
        return Request.create(Request.HttpMethod.POST, "http://mock",
                Map.of(), null, StandardCharsets.UTF_8, null);
    }

    private FeignException feignException() {
        return new FeignException.InternalServerError("mock error", feignRequest(), null, null);
    }

    private static void assertErrorCode(Throwable e, ErrorCode errorCode) {
        assertThat(e).isInstanceOf(CustomException.class);
        assertThat(((CustomException) e).getErrorCode()).isEqualTo(errorCode);
    }

    @Nested
    @DisplayName("정상 케이스")
    class Success {

        @BeforeEach
        void setUp() {
            givenExternalApisSucceed();
        }

        @Test
        public void MASTER가_주문하면_주문이_생성되고_응답이_반환된다() {
            //given
            loginAs("MASTER");

            //when
            CreateOrderResponse response = orderService.create(orderRequest());

            //then
            assertThat(response.getOrderId()).isNotNull();
            assertThat(response.getItemId()).isEqualTo(itemId);
            assertThat(response.getDeliveryId()).isEqualTo(deliveryId);
            //save()가 Mock이라 Auditing이 동작하지 않아 createdAt은 null
            assertThat(response.getCreatedAt()).isNull();
        }

        @Test
        public void MASTER도_유저를_조회하고_소속업체가_달라도_주문이_생성된다() {
            //given
            loginAs("MASTER");
            given(userServiceClient.fetchUserById(userId)).willReturn(new FetchUserByIdResponse(UUID.randomUUID()));

            //when
            CreateOrderResponse response = orderService.create(orderRequest());

            //then
            assertThat(response.getDeliveryId()).isEqualTo(deliveryId);
            verify(userServiceClient).fetchUserById(userId);
        }

        @Test
        public void 요청업체_소속_유저가_주문하면_유저조회_후_주문이_생성된다() {
            //given
            loginAs("COMPANY_MANAGER");
            given(userServiceClient.fetchUserById(userId)).willReturn(new FetchUserByIdResponse(requesterId));

            //when
            CreateOrderResponse response = orderService.create(orderRequest());

            //then
            assertThat(response.getDeliveryId()).isEqualTo(deliveryId);
            verify(userServiceClient).fetchUserById(userId);
        }

        @Test
        public void 공급업체_허브에서_요청수량만큼_재고감소를_요청한다() {
            //given
            loginAs("MASTER");

            //when
            orderService.create(orderRequest());

            //then
            verify(hubServiceClient).decreaseStock(supplierHubId, itemId, quantity);
        }

        @Test
        public void 공급업체_허브에서_요청업체_허브로_배송생성을_요청한다() {
            //given
            loginAs("MASTER");
            ArgumentCaptor<CreateDeliveryRequest> captor = ArgumentCaptor.forClass(CreateDeliveryRequest.class);

            //when
            CreateOrderResponse response = orderService.create(orderRequest());

            //then
            verify(deliveryServiceClient).createDelivery(captor.capture());
            CreateDeliveryRequest deliveryRequest = captor.getValue();

            assertThat(deliveryRequest.getOrderId()).isEqualTo(response.getOrderId());
            assertThat(deliveryRequest.getDepartureHubId()).isEqualTo(supplierHubId);
            assertThat(deliveryRequest.getDestinationHubId()).isEqualTo(requesterHubId);
            assertThat(deliveryRequest.getAddress()).isEqualTo("서울시 강남구 테헤란로 1");
            assertThat(deliveryRequest.getRecipientName()).isEqualTo("홍길동");
            assertThat(deliveryRequest.getRecipientPhone()).isEqualTo("010-1234-5678");
        }

        @Test
        public void 확정된_주문과_주문상품이_저장된다() {
            //given
            loginAs("MASTER");
            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            ArgumentCaptor<OrderItem> orderItemCaptor = ArgumentCaptor.forClass(OrderItem.class);

            //when
            CreateOrderResponse response = orderService.create(orderRequest());

            //then
            //WAITING 상태로 1번, 확정 후 1번 저장
            verify(orderTransactionService, times(2)).save(orderCaptor.capture(), orderItemCaptor.capture());
            Order order = orderCaptor.getValue();
            OrderItem orderItem = orderItemCaptor.getValue();

            //주문
            assertThat(order.getId()).isEqualTo(response.getOrderId());
            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(order.getDeliveryId()).isEqualTo(deliveryId);
            assertThat(order.getOrderUserId()).isEqualTo(userId);
            assertThat(order.getRequesterId()).isEqualTo(requesterId);
            assertThat(order.getDestinationHubId()).isEqualTo(requesterHubId);
            assertThat(order.getAddress()).isEqualTo("서울시 강남구 테헤란로 1");
            assertThat(order.getRequestNote()).isEqualTo("문 앞에 놓아주세요");

            //주문상품
            assertThat(orderItem.getOrder()).isSameAs(order);
            assertThat(orderItem.getItemId()).isEqualTo(itemId);
            assertThat(orderItem.getItemName()).isEqualTo("생수 2L");
            assertThat(orderItem.getPrice()).isEqualTo(price);
            //상품의 전체 재고(1,000)가 아닌 요청 수량
            assertThat(orderItem.getQuantity()).isEqualTo(quantity);
            assertThat(orderItem.getDepartureHubId()).isEqualTo(supplierHubId);
            assertThat(orderItem.getSupplierId()).isEqualTo(supplierId);
        }

        @Test
        public void 주문은_WAITING으로_먼저_저장된_뒤_CONFIRMED로_저장된다() {
            //given
            loginAs("MASTER");
            //같은 Order 객체가 변경되므로 저장 시점의 상태를 기록
            List<OrderStatus> savedStatuses = new ArrayList<>();
            willAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                savedStatuses.add(order.getOrderStatus());
                return null;
            }).given(orderTransactionService).save(any(), any());

            //when
            orderService.create(orderRequest());

            //then
            assertThat(savedStatuses).containsExactly(OrderStatus.WAITING, OrderStatus.CONFIRMED);
            verify(orderTransactionService, never()).failCreate(any(), anyBoolean());
        }

        @Test
        public void 상품조회_업체조회_주문저장_재고감소_배송생성_확정저장_순서로_실행된다() {
            //given
            loginAs("MASTER");
            InOrder inOrder = inOrder(companyServiceClient, hubServiceClient, deliveryServiceClient, orderTransactionService);

            //when
            orderService.create(orderRequest());

            //then
            inOrder.verify(companyServiceClient).fetchItemById(itemId);
            inOrder.verify(companyServiceClient).fetchCompanyById(requesterId);
            inOrder.verify(companyServiceClient).fetchCompanyById(supplierId);
            inOrder.verify(orderTransactionService).save(any(), any());
            inOrder.verify(hubServiceClient).decreaseStock(supplierHubId, itemId, quantity);
            inOrder.verify(deliveryServiceClient).createDelivery(any());
            inOrder.verify(orderTransactionService).save(any(), any());
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class Failure {

        @Test
        public void 요청업체와_공급업체가_같으면_외부호출_없이_실패한다() {
            //given
            loginAs("MASTER");
            CreateOrderRequest request = new CreateOrderRequest(requesterId, requesterId, itemId, price, quantity,
                    null, "홍길동", "010-1234-5678");

            //when, then
            assertThatThrownBy(() -> orderService.create(request))
                    .satisfies(e -> assertErrorCode(e, ErrorCode.C400_SAME_COMPANY));
            verifyNoInteractions(companyServiceClient, hubServiceClient, deliveryServiceClient, orderTransactionService);
        }

        @Test
        public void 유저를_먼저_조회한_뒤_같은업체_검증에_실패한다() {
            //given
            loginAs("COMPANY_MANAGER");
            given(userServiceClient.fetchUserById(userId)).willReturn(new FetchUserByIdResponse(requesterId));
            CreateOrderRequest request = new CreateOrderRequest(requesterId, requesterId, itemId, price, quantity,
                    null, "홍길동", "010-1234-5678");

            //when, then
            assertThatThrownBy(() -> orderService.create(request))
                    .satisfies(e -> assertErrorCode(e, ErrorCode.C400_SAME_COMPANY));
            verify(userServiceClient).fetchUserById(userId);
            verifyNoInteractions(companyServiceClient, hubServiceClient, deliveryServiceClient, orderTransactionService);
        }

        @Test
        public void 유저의_소속업체가_null이면_상품조회_전에_실패한다() {
            //given
            loginAs("COMPANY_MANAGER");
            given(userServiceClient.fetchUserById(userId)).willReturn(new FetchUserByIdResponse(null));

            //when, then
            assertThatThrownBy(() -> orderService.create(orderRequest()))
                    .satisfies(e -> assertErrorCode(e, ErrorCode.C403_FORBIDDEN_REQUESTER));
            verifyNoInteractions(companyServiceClient, hubServiceClient, deliveryServiceClient, orderTransactionService);
        }

        @Test
        public void 유저가_없으면_C404_USER_NOT_FOUND_예외가_발생하고_상품조회_전에_실패한다() {
            //given
            loginAs("COMPANY_MANAGER");
            given(userServiceClient.fetchUserById(userId))
                    .willThrow(new FeignException.NotFound("Not Found", feignRequest(), null, null));

            //when, then
            assertThatThrownBy(() -> orderService.create(orderRequest()))
                    .satisfies(e -> assertErrorCode(e, ErrorCode.C404_USER_NOT_FOUND));
            verifyNoInteractions(companyServiceClient, hubServiceClient, deliveryServiceClient, orderTransactionService);
        }

        @Test
        public void 유저조회가_404가_아닌_에러면_FeignException을_그대로_전달한다() {
            //given
            loginAs("COMPANY_MANAGER");
            given(userServiceClient.fetchUserById(userId)).willThrow(feignException());

            //when, then
            assertThatThrownBy(() -> orderService.create(orderRequest()))
                    .isInstanceOf(FeignException.InternalServerError.class);
            verifyNoInteractions(companyServiceClient, hubServiceClient, deliveryServiceClient, orderTransactionService);
        }

        @Test
        public void 소문자_master는_MASTER로_인정되지_않아_소속검증을_거친다() {
            //given
            loginAs("master");
            given(userServiceClient.fetchUserById(userId)).willReturn(new FetchUserByIdResponse(UUID.randomUUID()));

            //when, then
            assertThatThrownBy(() -> orderService.create(orderRequest()))
                    .satisfies(e -> assertErrorCode(e, ErrorCode.C403_FORBIDDEN_REQUESTER));
            verify(userServiceClient).fetchUserById(userId);
        }

        @Test
        public void 다른업체_소속_유저면_상품조회_전에_실패한다() {
            //given
            loginAs("COMPANY_MANAGER");
            given(userServiceClient.fetchUserById(userId)).willReturn(new FetchUserByIdResponse(UUID.randomUUID()));

            //when, then
            assertThatThrownBy(() -> orderService.create(orderRequest()))
                    .satisfies(e -> assertErrorCode(e, ErrorCode.C403_FORBIDDEN_REQUESTER));
            verifyNoInteractions(companyServiceClient, hubServiceClient, deliveryServiceClient, orderTransactionService);
        }

        @Test
        public void 상품가격이_변경되었으면_재고감소_전에_실패한다() {
            //given
            loginAs("MASTER");
            givenExternalApisSucceed();
            FetchItemByIdResponse changedItem = itemResponse();
            changedItem.setPrice(price + 1_000L);
            given(companyServiceClient.fetchItemById(itemId)).willReturn(changedItem);

            //when, then
            assertThatThrownBy(() -> orderService.create(orderRequest()))
                    .satisfies(e -> assertErrorCode(e, ErrorCode.C409_ORDER_PRICE_CHANGED));
            verify(companyServiceClient, never()).fetchCompanyById(any());
            verifyNoInteractions(hubServiceClient, deliveryServiceClient, orderTransactionService);
        }

        @Test
        public void 상품이_없으면_C400_ITEM_NOT_FOUND_예외가_발생하고_재고감소를_하지_않는다() {
            //given
            loginAs("MASTER");
            givenExternalApisSucceed();
            given(companyServiceClient.fetchItemById(itemId))
                    .willThrow(new FeignException.NotFound("Not Found", feignRequest(), null, null));

            //when, then
            assertThatThrownBy(() -> orderService.create(orderRequest()))
                    .satisfies(e -> assertErrorCode(e, ErrorCode.C404_ITEM_NOT_FOUND));
            verifyNoInteractions(hubServiceClient, deliveryServiceClient, orderTransactionService);
        }

        @Test
        public void 업체가_없으면_C400_COMPANY_NOT_FOUND_예외가_발생하고_재고감소를_하지_않는다() {
            //given
            loginAs("MASTER");
            givenExternalApisSucceed();
            given(companyServiceClient.fetchCompanyById(supplierId))
                    .willThrow(new FeignException.NotFound("Not Found", feignRequest(), null, null));

            //when, then
            assertThatThrownBy(() -> orderService.create(orderRequest()))
                    .satisfies(e -> assertErrorCode(e, ErrorCode.C404_COMPANY_NOT_FOUND));
            verifyNoInteractions(hubServiceClient, deliveryServiceClient, orderTransactionService);
        }

        @Test
        public void 상품조회가_404가_아닌_에러면_FeignException을_그대로_전달한다() {
            //given
            loginAs("MASTER");
            givenExternalApisSucceed();
            given(companyServiceClient.fetchItemById(itemId)).willThrow(feignException());

            //when, then
            assertThatThrownBy(() -> orderService.create(orderRequest()))
                    .isInstanceOf(FeignException.InternalServerError.class);
            verifyNoInteractions(hubServiceClient, deliveryServiceClient, orderTransactionService);
        }

        @Test
        public void WAITING_주문저장에_실패하면_재고감소와_보상을_하지_않는다() {
            //given
            loginAs("MASTER");
            givenExternalApisSucceed();
            RuntimeException dbError = new RuntimeException("DB 오류");
            willThrow(dbError).given(orderTransactionService).save(any(), any());

            //when, then
            assertThatThrownBy(() -> orderService.create(orderRequest())).isSameAs(dbError);
            verifyNoInteractions(hubServiceClient, deliveryServiceClient);
            verify(orderTransactionService, never()).failCreate(any(), anyBoolean());
        }
    }

    @Nested
    @DisplayName("실패 케이스 - 주문생성 보상처리")
    class CreateCompensation {

        @BeforeEach
        void setUp() {
            loginAs("MASTER");
            givenExternalApisSucceed();
            given(hubServiceClient.restoreStock(supplierHubId, itemId, quantity)).willReturn("OK");
            given(deliveryServiceClient.cancelDelivery(deliveryId))
                    .willReturn(new CancelDeliveryResponse(deliveryId, "CANCELLED"));
        }

        //WAITING 저장 시점에 생성된 주문ID
        private UUID savedOrderId() {
            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderTransactionService, atLeastOnce()).save(captor.capture(), any());
            return captor.getAllValues().get(0).getId();
        }

        @Test
        public void 재고감소에_실패하면_보상없이_FAILED로_변경하고_원래_예외를_전달한다() {
            //given
            FeignException stockError = feignException();
            willThrow(stockError).given(hubServiceClient).decreaseStock(supplierHubId, itemId, quantity);

            //when, then
            assertThatThrownBy(() -> orderService.create(orderRequest())).isSameAs(stockError);
            verifyNoInteractions(deliveryServiceClient);
            verify(hubServiceClient, never()).restoreStock(any(), any(), any());
            UUID orderId = savedOrderId();
            verify(orderTransactionService).failCreate(orderId, true);
        }

        @Test
        public void 배송생성에_실패하면_재고를_복구하고_FAILED로_변경한다() {
            //given
            FeignException deliveryError = feignException();
            given(deliveryServiceClient.createDelivery(any())).willThrow(deliveryError);

            //when, then
            assertThatThrownBy(() -> orderService.create(orderRequest())).isSameAs(deliveryError);
            verify(hubServiceClient).restoreStock(supplierHubId, itemId, quantity);
            verify(deliveryServiceClient, never()).cancelDelivery(any());
            verify(deliveryServiceClient, never()).deleteDelivery(any());
            UUID orderId = savedOrderId();
            verify(orderTransactionService).failCreate(orderId, true);
        }

        @Test
        public void 배송생성_응답에_배송ID가_없으면_C500_예외가_발생하고_재고만_복구한다() {
            //given
            given(deliveryServiceClient.createDelivery(any()))
                    .willReturn(new CreateDeliveryResponse(null, null, "WAITING"));

            //when, then
            assertThatThrownBy(() -> orderService.create(orderRequest()))
                    .satisfies(e -> assertErrorCode(e, ErrorCode.C500_INTERNAL_SERVER_ERROR));
            verify(hubServiceClient).restoreStock(supplierHubId, itemId, quantity);
            verify(deliveryServiceClient, never()).cancelDelivery(any());
            UUID orderId = savedOrderId();
            verify(orderTransactionService).failCreate(orderId, true);
        }

        @Test
        public void 확정저장에_실패하면_배송취소_배송삭제_재고복구_순서로_보상한다() {
            //given
            RuntimeException dbError = new RuntimeException("DB 오류");
            willDoNothing().willThrow(dbError).given(orderTransactionService).save(any(), any());
            InOrder inOrder = inOrder(deliveryServiceClient, hubServiceClient, orderTransactionService);

            //when, then
            assertThatThrownBy(() -> orderService.create(orderRequest())).isSameAs(dbError);
            inOrder.verify(deliveryServiceClient).cancelDelivery(deliveryId);
            inOrder.verify(deliveryServiceClient).deleteDelivery(deliveryId);
            inOrder.verify(hubServiceClient).restoreStock(supplierHubId, itemId, quantity);
            UUID orderId = savedOrderId();
            inOrder.verify(orderTransactionService).failCreate(orderId, true);
        }

        @Test
        public void 보상이_한번_실패해도_재시도에_성공하면_FAILED로_변경한다() {
            //given
            given(deliveryServiceClient.createDelivery(any())).willThrow(feignException());
            given(hubServiceClient.restoreStock(supplierHubId, itemId, quantity))
                    .willThrow(feignException())
                    .willReturn("OK");

            //when, then
            assertThatThrownBy(() -> orderService.create(orderRequest())).isInstanceOf(FeignException.class);
            verify(hubServiceClient, times(2)).restoreStock(supplierHubId, itemId, quantity);
            UUID orderId = savedOrderId();
            verify(orderTransactionService).failCreate(orderId, true);
        }

        @Test
        public void 재시도까지_실패하면_COMPENSATION_FAILED로_변경하고_원래_예외를_전달한다() {
            //given
            FeignException deliveryError = feignException();
            given(deliveryServiceClient.createDelivery(any())).willThrow(deliveryError);
            given(hubServiceClient.restoreStock(supplierHubId, itemId, quantity))
                    .willThrow(new RuntimeException("재고복구 실패"));

            //when, then
            //보상 예외가 아닌 원래 실패 원인이 전달되어야 함
            assertThatThrownBy(() -> orderService.create(orderRequest())).isSameAs(deliveryError);
            verify(hubServiceClient, times(2)).restoreStock(supplierHubId, itemId, quantity);
            UUID orderId = savedOrderId();
            verify(orderTransactionService).failCreate(orderId, false);
        }

        @Test
        public void 배송취소가_최종_실패해도_재고복구는_수행하고_COMPENSATION_FAILED로_변경한다() {
            //given
            willDoNothing().willThrow(new RuntimeException("DB 오류"))
                    .given(orderTransactionService).save(any(), any());
            given(deliveryServiceClient.cancelDelivery(deliveryId)).willThrow(feignException());

            //when, then
            assertThatThrownBy(() -> orderService.create(orderRequest())).isInstanceOf(RuntimeException.class);
            verify(deliveryServiceClient, times(2)).cancelDelivery(deliveryId);
            //취소되지 않은 배송은 삭제할 수 없으므로 삭제는 시도하지 않음
            verify(deliveryServiceClient, never()).deleteDelivery(any());
            verify(hubServiceClient).restoreStock(supplierHubId, itemId, quantity);
            UUID orderId = savedOrderId();
            verify(orderTransactionService).failCreate(orderId, false);
        }

        @Test
        public void 주문상태_변경에_실패해도_원래_예외를_전달한다() {
            //given
            FeignException deliveryError = feignException();
            given(deliveryServiceClient.createDelivery(any())).willThrow(deliveryError);
            willThrow(new RuntimeException("DB 오류")).given(orderTransactionService).failCreate(any(), anyBoolean());

            //when, then
            assertThatThrownBy(() -> orderService.create(orderRequest())).isSameAs(deliveryError);
            verify(hubServiceClient).restoreStock(supplierHubId, itemId, quantity);
        }
    }

    @Nested
    @DisplayName("getOrderById()")
    class GetOrderById {

        private OrderItem savedOrderItem(UUID orderUserId) {
            Order order = new Order(orderUserId, requesterId, requesterHubId, "서울시 강남구 테헤란로 1", "문 앞에 놓아주세요");
            order.confirm(deliveryId);
            return new OrderItem(itemId, "생수 2L", price, quantity, supplierHubId, supplierId, order);
        }

        @Test
        public void 본인이_생성한_주문이면_주문정보를_반환한다() {
            //given
            loginAs("COMPANY_MANAGER");
            OrderItem orderItem = savedOrderItem(userId);
            UUID orderId = orderItem.getOrder().getId();
            given(orderTransactionService.findWithOrder(orderId)).willReturn(orderItem);

            //when
            OrderDetailResponse response = orderService.getOrderById(orderId);

            //then
            assertThat(response.getOrderId()).isEqualTo(orderId);
            assertThat(response.getDeliveryId()).isEqualTo(deliveryId);
            assertThat(response.getOrderUserId()).isEqualTo(userId);
            assertThat(response.getRequesterId()).isEqualTo(requesterId);
            assertThat(response.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(response.getAddress()).isEqualTo("서울시 강남구 테헤란로 1");
            assertThat(response.getRequestNote()).isEqualTo("문 앞에 놓아주세요");

            assertThat(response.getItem().getItemId()).isEqualTo(itemId);
            assertThat(response.getItem().getSupplierId()).isEqualTo(supplierId);
            assertThat(response.getItem().getItemName()).isEqualTo("생수 2L");
            assertThat(response.getItem().getPrice()).isEqualTo(price);
            assertThat(response.getItem().getQuantity()).isEqualTo(quantity);
            assertThat(response.getItem().getTotalPrice()).isEqualTo(price * quantity);
        }

        @Test
        public void MASTER는_다른_사람의_주문도_조회한다() {
            //given
            loginAs("MASTER");
            OrderItem orderItem = savedOrderItem(UUID.randomUUID());
            UUID orderId = orderItem.getOrder().getId();
            given(orderTransactionService.findWithOrder(orderId)).willReturn(orderItem);

            //when, then
            assertThat(orderService.getOrderById(orderId).getOrderId()).isEqualTo(orderId);
        }

        @Test
        public void 다른_사람의_주문이면_C403_예외가_발생한다() {
            //given
            loginAs("COMPANY_MANAGER");
            OrderItem orderItem = savedOrderItem(UUID.randomUUID());
            UUID orderId = orderItem.getOrder().getId();
            given(orderTransactionService.findWithOrder(orderId)).willReturn(orderItem);

            //when, then
            assertThatThrownBy(() -> orderService.getOrderById(orderId))
                    .satisfies(e -> assertErrorCode(e, ErrorCode.C403_FORBIDDEN_ORDER));
        }

        @Test
        public void 없는_주문이면_C404_예외가_전달된다() {
            //given
            loginAs("MASTER");
            UUID orderId = UUID.randomUUID();
            given(orderTransactionService.findWithOrder(orderId))
                    .willThrow(new CustomException(ErrorCode.C404_ORDER_NOT_FOUND));

            //when, then
            assertThatThrownBy(() -> orderService.getOrderById(orderId))
                    .satisfies(e -> assertErrorCode(e, ErrorCode.C404_ORDER_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("cancelOrderById()")
    class CancelOrderById {

        private final Request request = Request.create(Request.HttpMethod.POST, "http://delivery-service",
                Map.of(), null, StandardCharsets.UTF_8, null);

        //주문취소(검증 포함) → 배송취소 성공까지 정상 동작하는 상황
        private Order givenCancelableOrder() {
            Order order = new Order(userId, requesterId, requesterHubId, "서울시 강남구 테헤란로 1", null);
            order.confirm(deliveryId);
            given(orderTransactionService.cancel(order.getId())).willAnswer(invocation -> {
                order.cancel();
                return order;
            });
            given(deliveryServiceClient.cancelDelivery(deliveryId))
                    .willReturn(new CancelDeliveryResponse(deliveryId, "CANCELLED"));
            return order;
        }

        @Nested
        @DisplayName("정상 케이스")
        class Success {

            @Test
            public void 주문을_취소하면_배송취소_후_취소된_주문정보를_반환한다() {
                //given
                loginAs("COMPANY_MANAGER");
                Order order = givenCancelableOrder();

                //when
                CancelOrderResponse response = orderService.cancelOrderById(order.getId());

                //then
                assertThat(response.getOrderId()).isEqualTo(order.getId());
                assertThat(response.getDeliveryId()).isEqualTo(deliveryId);
                assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
                verify(deliveryServiceClient).cancelDelivery(deliveryId);
            }

            @Test
            public void 주문을_먼저_취소한_뒤_배송취소를_요청한다() {
                //given
                loginAs("MASTER");
                Order order = givenCancelableOrder();
                InOrder inOrder = inOrder(orderTransactionService, deliveryServiceClient);

                //when
                orderService.cancelOrderById(order.getId());

                //then
                inOrder.verify(orderTransactionService).cancel(order.getId());
                inOrder.verify(deliveryServiceClient).cancelDelivery(deliveryId);
            }

            @Test
            public void 배송취소에_성공하면_보상처리를_하지_않는다() {
                //given
                loginAs("MASTER");
                Order order = givenCancelableOrder();

                //when
                orderService.cancelOrderById(order.getId());

                //then
                verify(orderTransactionService, never()).revertCancel(any());
            }
        }

        @Nested
        @DisplayName("실패 케이스 - 주문취소 단계")
        class CancelFailure {

            //주문취소(조회/권한/상태 검증) 단계에서 실패하면 배송취소와 보상처리 모두 하지 않음
            private void assertNoDeliveryCallAndNoCompensation() {
                verifyNoInteractions(deliveryServiceClient);
                verify(orderTransactionService, never()).revertCancel(any());
            }

            @Test
            public void 없는_주문이면_C404_예외가_전달된다() {
                //given
                loginAs("MASTER");
                UUID orderId = UUID.randomUUID();
                given(orderTransactionService.cancel(orderId))
                        .willThrow(new CustomException(ErrorCode.C404_ORDER_NOT_FOUND));

                //when, then
                assertThatThrownBy(() -> orderService.cancelOrderById(orderId))
                        .satisfies(e -> assertErrorCode(e, ErrorCode.C404_ORDER_NOT_FOUND));
                assertNoDeliveryCallAndNoCompensation();
            }

            @Test
            public void 다른_사람의_주문이면_C403_예외가_전달된다() {
                //given
                loginAs("COMPANY_MANAGER");
                UUID orderId = UUID.randomUUID();
                given(orderTransactionService.cancel(orderId))
                        .willThrow(new CustomException(ErrorCode.C403_FORBIDDEN_ORDER));

                //when, then
                assertThatThrownBy(() -> orderService.cancelOrderById(orderId))
                        .satisfies(e -> assertErrorCode(e, ErrorCode.C403_FORBIDDEN_ORDER));
                assertNoDeliveryCallAndNoCompensation();
            }

            @Test
            public void 취소할_수_없는_상태면_C409_예외가_전달된다() {
                //given
                loginAs("MASTER");
                UUID orderId = UUID.randomUUID();
                given(orderTransactionService.cancel(orderId))
                        .willThrow(new CustomException(ErrorCode.C409_INVALID_ORDER_STATUS));

                //when, then
                assertThatThrownBy(() -> orderService.cancelOrderById(orderId))
                        .satisfies(e -> assertErrorCode(e, ErrorCode.C409_INVALID_ORDER_STATUS));
                assertNoDeliveryCallAndNoCompensation();
            }
        }

        @Nested
        @DisplayName("실패 케이스 - 배송취소 단계 (보상처리)")
        class DeliveryFailure {

            @Test
            public void 배송이_이미_출발해_409로_응답하면_취소불가_예외가_발생하고_주문을_복구한다() {
                //given
                loginAs("MASTER");
                Order order = givenCancelableOrder();
                given(deliveryServiceClient.cancelDelivery(deliveryId))
                        .willThrow(new FeignException.Conflict("출발 전 배송만 취소할 수 있습니다.", request, null, null));

                //when, then
                assertThatThrownBy(() -> orderService.cancelOrderById(order.getId()))
                        .satisfies(e -> assertErrorCode(e, ErrorCode.C409_ORDER_NOT_CANCELABLE));
                verify(orderTransactionService).revertCancel(order.getId());
            }

            @Test
            public void 배송이_없어_404로_응답하면_C404_DELIVERY_NOT_FOUND_예외가_발생하고_주문을_복구한다() {
                //given
                loginAs("MASTER");
                Order order = givenCancelableOrder();
                given(deliveryServiceClient.cancelDelivery(deliveryId))
                        .willThrow(new FeignException.NotFound("해당 배송을 찾을 수 없습니다.", request, null, null));

                //when, then
                assertThatThrownBy(() -> orderService.cancelOrderById(order.getId()))
                        .satisfies(e -> assertErrorCode(e, ErrorCode.C404_DELIVERY_NOT_FOUND));
                verify(orderTransactionService).revertCancel(order.getId());
            }

            @Test
            public void 배송취소_응답상태가_CANCELLED가_아니면_취소불가_예외가_발생하고_주문을_복구한다() {
                //given
                loginAs("MASTER");
                Order order = givenCancelableOrder();
                given(deliveryServiceClient.cancelDelivery(deliveryId))
                        .willReturn(new CancelDeliveryResponse(deliveryId, "HUB_IN_TRANSIT"));

                //when, then
                assertThatThrownBy(() -> orderService.cancelOrderById(order.getId()))
                        .satisfies(e -> assertErrorCode(e, ErrorCode.C409_ORDER_NOT_CANCELABLE));
                verify(orderTransactionService).revertCancel(order.getId());
            }

            @Test
            public void 배송서비스가_5xx로_응답하면_FeignException을_그대로_전달하고_주문을_복구한다() {
                //given
                loginAs("MASTER");
                Order order = givenCancelableOrder();
                given(deliveryServiceClient.cancelDelivery(deliveryId)).willThrow(feignException());

                //when, then
                assertThatThrownBy(() -> orderService.cancelOrderById(order.getId()))
                        .isInstanceOf(FeignException.InternalServerError.class);
                verify(orderTransactionService).revertCancel(order.getId());
            }

            @Test
            public void 배송서비스가_응답하지_않으면_FeignException을_그대로_전달하고_주문을_복구한다() {
                //given
                loginAs("MASTER");
                Order order = givenCancelableOrder();
                given(deliveryServiceClient.cancelDelivery(deliveryId))
                        .willThrow(new RetryableException(-1, "Read timed out", Request.HttpMethod.POST, (Long) null, request));

                //when, then
                assertThatThrownBy(() -> orderService.cancelOrderById(order.getId()))
                        .isInstanceOf(RetryableException.class);
                verify(orderTransactionService).revertCancel(order.getId());
            }

            @Test
            public void 그_외_예외가_발생해도_예외를_그대로_전달하고_주문을_복구한다() {
                //given
                loginAs("MASTER");
                Order order = givenCancelableOrder();
                given(deliveryServiceClient.cancelDelivery(deliveryId))
                        .willThrow(new IllegalStateException("circuit open"));

                //when, then
                assertThatThrownBy(() -> orderService.cancelOrderById(order.getId()))
                        .isInstanceOf(IllegalStateException.class);
                verify(orderTransactionService).revertCancel(order.getId());
            }

            @Test
            public void 주문복구가_실패해도_1회_재시도하고_원래_예외를_그대로_전달한다() {
                //given
                loginAs("MASTER");
                Order order = givenCancelableOrder();
                given(deliveryServiceClient.cancelDelivery(deliveryId)).willThrow(feignException());
                willThrow(new RuntimeException("DB 오류")).given(orderTransactionService).revertCancel(order.getId());

                //when, then
                assertThatThrownBy(() -> orderService.cancelOrderById(order.getId()))
                        .isInstanceOf(FeignException.InternalServerError.class);
                verify(orderTransactionService, times(2)).revertCancel(order.getId());
            }
        }
    }

    @Nested
    @DisplayName("deleteOrderById()")
    class DeleteOrderById {

        private final Request request = Request.create(Request.HttpMethod.DELETE, "http://delivery-service",
                Map.of(), null, StandardCharsets.UTF_8, null);

        //주문삭제(검증 포함) → 배송삭제 성공까지 정상 동작하는 상황
        private Order givenDeletableOrder() {
            Order order = new Order(userId, requesterId, requesterHubId, "서울시 강남구 테헤란로 1", null);
            order.confirm(deliveryId);
            order.cancel();
            given(orderTransactionService.delete(order.getId(), userId)).willAnswer(invocation -> {
                order.delete(userId);
                return order;
            });
            return order;
        }

        @Nested
        @DisplayName("정상 케이스")
        class Success {

            @Test
            public void 주문을_삭제하면_배송삭제를_요청한다() {
                //given
                loginAs("COMPANY_MANAGER");
                Order order = givenDeletableOrder();

                //when
                orderService.deleteOrderById(order.getId());

                //then
                verify(orderTransactionService).delete(order.getId(), userId);
                verify(deliveryServiceClient).deleteDelivery(deliveryId);
            }

            @Test
            public void 주문을_먼저_삭제한_뒤_배송삭제를_요청한다() {
                //given
                loginAs("MASTER");
                Order order = givenDeletableOrder();
                InOrder inOrder = inOrder(orderTransactionService, deliveryServiceClient);

                //when
                orderService.deleteOrderById(order.getId());

                //then
                inOrder.verify(orderTransactionService).delete(order.getId(), userId);
                inOrder.verify(deliveryServiceClient).deleteDelivery(deliveryId);
            }

            @Test
            public void 배송삭제에_성공하면_보상처리를_하지_않는다() {
                //given
                loginAs("MASTER");
                Order order = givenDeletableOrder();

                //when
                orderService.deleteOrderById(order.getId());

                //then
                verify(orderTransactionService, never()).revertDelete(any());
            }
        }

        @Nested
        @DisplayName("실패 케이스 - 주문삭제 단계")
        class DeleteFailure {

            //주문삭제(조회/권한/상태 검증) 단계에서 실패하면 배송삭제와 보상처리 모두 하지 않음
            private void assertNoDeliveryCallAndNoCompensation() {
                verifyNoInteractions(deliveryServiceClient);
                verify(orderTransactionService, never()).revertDelete(any());
            }

            @Test
            public void 없는_주문이면_C404_예외가_전달된다() {
                //given
                loginAs("MASTER");
                UUID orderId = UUID.randomUUID();
                given(orderTransactionService.delete(orderId, userId))
                        .willThrow(new CustomException(ErrorCode.C404_ORDER_NOT_FOUND));

                //when, then
                assertThatThrownBy(() -> orderService.deleteOrderById(orderId))
                        .satisfies(e -> assertErrorCode(e, ErrorCode.C404_ORDER_NOT_FOUND));
                assertNoDeliveryCallAndNoCompensation();
            }

            @Test
            public void 다른_사람의_주문이면_C403_예외가_전달된다() {
                //given
                loginAs("COMPANY_MANAGER");
                UUID orderId = UUID.randomUUID();
                given(orderTransactionService.delete(orderId, userId))
                        .willThrow(new CustomException(ErrorCode.C403_FORBIDDEN_ORDER));

                //when, then
                assertThatThrownBy(() -> orderService.deleteOrderById(orderId))
                        .satisfies(e -> assertErrorCode(e, ErrorCode.C403_FORBIDDEN_ORDER));
                assertNoDeliveryCallAndNoCompensation();
            }

            @Test
            public void 취소되지_않은_주문이면_C409_예외가_전달된다() {
                //given
                loginAs("MASTER");
                UUID orderId = UUID.randomUUID();
                given(orderTransactionService.delete(orderId, userId))
                        .willThrow(new CustomException(ErrorCode.C409_INVALID_ORDER_STATUS));

                //when, then
                assertThatThrownBy(() -> orderService.deleteOrderById(orderId))
                        .satisfies(e -> assertErrorCode(e, ErrorCode.C409_INVALID_ORDER_STATUS));
                assertNoDeliveryCallAndNoCompensation();
            }
        }

        @Nested
        @DisplayName("실패 케이스 - 배송삭제 단계 (보상처리)")
        class DeliveryFailure {

            @Test
            public void 배송이_취소되지_않아_409로_응답하면_삭제불가_예외가_발생하고_주문을_복구한다() {
                //given
                loginAs("MASTER");
                Order order = givenDeletableOrder();
                willThrow(new FeignException.Conflict("취소된 배송만 삭제할 수 있습니다.", request, null, null))
                        .given(deliveryServiceClient).deleteDelivery(deliveryId);

                //when, then
                assertThatThrownBy(() -> orderService.deleteOrderById(order.getId()))
                        .satisfies(e -> assertErrorCode(e, ErrorCode.C409_ORDER_NOT_DELETABLE));
                verify(orderTransactionService).revertDelete(order.getId());
            }

            @Test
            public void 배송이_없어_404로_응답하면_C404_DELIVERY_NOT_FOUND_예외가_발생하고_주문을_복구한다() {
                //given
                loginAs("MASTER");
                Order order = givenDeletableOrder();
                willThrow(new FeignException.NotFound("해당 배송을 찾을 수 없습니다.", request, null, null))
                        .given(deliveryServiceClient).deleteDelivery(deliveryId);

                //when, then
                assertThatThrownBy(() -> orderService.deleteOrderById(order.getId()))
                        .satisfies(e -> assertErrorCode(e, ErrorCode.C404_DELIVERY_NOT_FOUND));
                verify(orderTransactionService).revertDelete(order.getId());
            }

            @Test
            public void 배송서비스가_5xx로_응답하면_FeignException을_그대로_전달하고_주문을_복구한다() {
                //given
                loginAs("MASTER");
                Order order = givenDeletableOrder();
                willThrow(feignException()).given(deliveryServiceClient).deleteDelivery(deliveryId);

                //when, then
                assertThatThrownBy(() -> orderService.deleteOrderById(order.getId()))
                        .isInstanceOf(FeignException.InternalServerError.class);
                verify(orderTransactionService).revertDelete(order.getId());
            }

            @Test
            public void 배송서비스가_응답하지_않으면_FeignException을_그대로_전달하고_주문을_복구한다() {
                //given
                loginAs("MASTER");
                Order order = givenDeletableOrder();
                willThrow(new RetryableException(-1, "Read timed out", Request.HttpMethod.DELETE, (Long) null, request))
                        .given(deliveryServiceClient).deleteDelivery(deliveryId);

                //when, then
                assertThatThrownBy(() -> orderService.deleteOrderById(order.getId()))
                        .isInstanceOf(RetryableException.class);
                verify(orderTransactionService).revertDelete(order.getId());
            }

            @Test
            public void 그_외_예외가_발생해도_예외를_그대로_전달하고_주문을_복구한다() {
                //given
                loginAs("MASTER");
                Order order = givenDeletableOrder();
                willThrow(new IllegalStateException("circuit open"))
                        .given(deliveryServiceClient).deleteDelivery(deliveryId);

                //when, then
                assertThatThrownBy(() -> orderService.deleteOrderById(order.getId()))
                        .isInstanceOf(IllegalStateException.class);
                verify(orderTransactionService).revertDelete(order.getId());
            }

            @Test
            public void 주문복구가_실패해도_1회_재시도하고_원래_예외를_그대로_전달한다() {
                //given
                loginAs("MASTER");
                Order order = givenDeletableOrder();
                willThrow(feignException()).given(deliveryServiceClient).deleteDelivery(deliveryId);
                willThrow(new RuntimeException("DB 오류")).given(orderTransactionService).revertDelete(order.getId());

                //when, then
                assertThatThrownBy(() -> orderService.deleteOrderById(order.getId()))
                        .isInstanceOf(FeignException.InternalServerError.class);
                verify(orderTransactionService, times(2)).revertDelete(order.getId());
            }
        }
    }

    @Nested
    @DisplayName("getOrdersByStatus()")
    class GetOrdersByStatus {

        private OrderItem confirmedOrderItem() {
            Order order = new Order(userId, requesterId, requesterHubId, "서울시 강남구 테헤란로 1", null);
            order.confirm(deliveryId);
            return new OrderItem(itemId, "생수 2L", price, quantity, supplierHubId, supplierId, order);
        }

        @Nested
        @DisplayName("정상 케이스")
        class Success {

            @Test
            public void MASTER는_유저_조건_없이_전체_주문을_조회한다() {
                //given
                loginAs("MASTER");
                OrderItem orderItem = confirmedOrderItem();
                given(orderTransactionService.findAllWithOrder(null, null)).willReturn(List.of(orderItem));

                //when
                OrderListResponse response = orderService.getOrdersByStatus(null);

                //then
                assertThat(response.getOrders()).hasSize(1);
                assertThat(response.getOrders().getFirst().getOrderId()).isEqualTo(orderItem.getOrder().getId());
                verify(orderTransactionService).findAllWithOrder(null, null);
            }

            @Test
            public void MASTER가_상태를_지정하면_해당_상태로_조회한다() {
                //given
                loginAs("MASTER");
                given(orderTransactionService.findAllWithOrder(null, OrderStatus.CONFIRMED)).willReturn(List.of());

                //when
                orderService.getOrdersByStatus(OrderStatus.CONFIRMED);

                //then
                verify(orderTransactionService).findAllWithOrder(null, OrderStatus.CONFIRMED);
            }

            @Test
            public void 일반_유저는_본인_주문만_조회한다() {
                //given
                loginAs("COMPANY_MANAGER");
                given(orderTransactionService.findAllWithOrder(userId, null)).willReturn(List.of());

                //when
                orderService.getOrdersByStatus(null);

                //then
                verify(orderTransactionService).findAllWithOrder(userId, null);
            }

            @Test
            public void 일반_유저가_상태를_지정하면_본인의_해당_상태_주문만_조회한다() {
                //given
                loginAs("COMPANY_MANAGER");
                given(orderTransactionService.findAllWithOrder(userId, OrderStatus.WAITING)).willReturn(List.of());

                //when
                orderService.getOrdersByStatus(OrderStatus.WAITING);

                //then
                verify(orderTransactionService).findAllWithOrder(userId, OrderStatus.WAITING);
            }

            @Test
            public void 조회된_주문이_없으면_빈_목록을_반환한다() {
                //given
                loginAs("MASTER");
                given(orderTransactionService.findAllWithOrder(null, null)).willReturn(List.of());

                //when
                OrderListResponse response = orderService.getOrdersByStatus(null);

                //then
                assertThat(response.getOrders()).isEmpty();
            }
        }
    }
}

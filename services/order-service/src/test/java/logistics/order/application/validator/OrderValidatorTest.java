package logistics.order.application.validator;

import logistics.order.common.AuthHeaders;
import logistics.order.domain.entity.Order;
import logistics.order.infrastructure.client.dto.CancelDeliveryResponse;
import logistics.order.infrastructure.client.dto.FetchItemByIdResponse;
import logistics.order.infrastructure.client.dto.FetchUserByIdResponse;
import logistics.order.presentation.dto.request.CreateOrderRequest;
import logistics.order.common.exception.CustomException;
import logistics.order.common.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderValidatorTest {

    private final OrderValidator orderValidator = new OrderValidator();

    private final UUID userId = UUID.randomUUID();
    private final UUID requesterId = UUID.randomUUID();
    private final UUID supplierId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();
    private final Long price = 10_000L;

    private MockHttpServletRequest httpRequest;

    @BeforeEach
    void setUp() {
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

    private CreateOrderRequest orderRequest(UUID requesterId, UUID supplierId, Long price) {
        return new CreateOrderRequest(requesterId, supplierId, itemId, price, 1L,
                "요청사항", "수령인", "010-0000-0000");
    }

    private FetchItemByIdResponse item(UUID companyId, Long price) {
        FetchItemByIdResponse item = new FetchItemByIdResponse();
        item.setItemId(itemId);
        item.setCompanyId(companyId);
        item.setPrice(price);
        return item;
    }

    private static void assertErrorCode(Throwable e, ErrorCode errorCode) {
        assertThat(e).isInstanceOf(CustomException.class);
        assertThat(((CustomException) e).getErrorCode()).isEqualTo(errorCode);
    }

    @Nested
    @DisplayName("validateNotSameCompany()")
    class ValidateNotSameCompany {

        @Test
        public void 요청업체와_공급업체가_다르면_통과한다() {
            //when, then
            assertThatCode(() -> orderValidator.validateNotSameCompany(requesterId, supplierId))
                    .doesNotThrowAnyException();
        }

        @Test
        public void 요청업체와_공급업체가_같으면_C400_SAME_COMPANY_예외가_발생한다() {
            //when, then
            assertThatThrownBy(() -> orderValidator.validateNotSameCompany(requesterId, requesterId))
                    .satisfies(e -> assertErrorCode(e, ErrorCode.C400_SAME_COMPANY));
        }
    }

    @Nested
    @DisplayName("validateUserBelongsToRequester()")
    class ValidateUserBelongsToRequester {

        @Nested
        @DisplayName("정상 케이스")
        class Success {

            @Test
            public void 요청업체_소속_유저는_통과한다() {
                //given
                loginAs("COMPANY_MANAGER");

                //when, then
                assertThatCode(() -> orderValidator.validateUserBelongsToRequester(requesterId, requesterId))
                        .doesNotThrowAnyException();
            }

            @Test
            public void MASTER는_소속업체가_달라도_통과한다() {
                //given
                loginAs("MASTER");

                //when, then
                assertThatCode(() -> orderValidator.validateUserBelongsToRequester(UUID.randomUUID(), requesterId))
                        .doesNotThrowAnyException();
            }

            @Test
            public void MASTER는_소속업체가_없어도_통과한다() {
                //given
                loginAs("MASTER");

                //when, then
                assertThatCode(() -> orderValidator.validateUserBelongsToRequester(null, requesterId))
                        .doesNotThrowAnyException();
            }
        }

        @Nested
        @DisplayName("실패 케이스")
        class Failure {

            @Test
            public void 다른업체_소속_유저면_C403_FORBIDDEN_REQUESTER_예외가_발생한다() {
                //given
                loginAs("COMPANY_MANAGER");

                //when, then
                assertThatThrownBy(() -> orderValidator.validateUserBelongsToRequester(UUID.randomUUID(), requesterId))
                        .satisfies(e -> assertErrorCode(e, ErrorCode.C403_FORBIDDEN_REQUESTER));
            }

            @Test
            public void 유저의_소속업체가_null이면_C403_FORBIDDEN_REQUESTER_예외가_발생한다() {
                //given
                loginAs("COMPANY_MANAGER");

                //when, then
                assertThatThrownBy(() -> orderValidator.validateUserBelongsToRequester(null, requesterId))
                        .satisfies(e -> assertErrorCode(e, ErrorCode.C403_FORBIDDEN_REQUESTER));
            }

            @Test
            public void 소문자_master는_MASTER로_인정되지_않아_소속검증을_거친다() {
                //given
                loginAs("master");

                //when, then
                assertThatThrownBy(() -> orderValidator.validateUserBelongsToRequester(UUID.randomUUID(), requesterId))
                        .satisfies(e -> assertErrorCode(e, ErrorCode.C403_FORBIDDEN_REQUESTER));
            }
        }
    }

    @Nested
    @DisplayName("validateOrderOwner()")
    class ValidateOrderOwner {

        private Order orderOf(UUID orderUserId) {
            return new Order(orderUserId, requesterId, UUID.randomUUID(), "서울시 강남구", null);
        }

        @Nested
        @DisplayName("정상 케이스")
        class Success {

            @Test
            public void 본인이_생성한_주문이면_통과한다() {
                //given
                loginAs("COMPANY_MANAGER");

                //when, then
                assertThatCode(() -> orderValidator.validateOrderOwner(userId))
                        .doesNotThrowAnyException();
            }

            @Test
            public void MASTER는_다른_사람의_주문도_통과한다() {
                //given
                loginAs("MASTER");

                //when, then
                assertThatCode(() -> orderValidator.validateOrderOwner(UUID.randomUUID()))
                        .doesNotThrowAnyException();
            }
        }

        @Nested
        @DisplayName("실패 케이스")
        class Failure {

            @Test
            public void 다른_사람의_주문이면_C403_FORBIDDEN_ORDER_예외가_발생한다() {
                //given
                loginAs("COMPANY_MANAGER");

                //when, then
                assertThatThrownBy(() -> orderValidator.validateOrderOwner(UUID.randomUUID()))
                        .satisfies(e -> assertErrorCode(e, ErrorCode.C403_FORBIDDEN_ORDER));
            }
        }
    }

    @Nested
    @DisplayName("validateDeliveryCanceled()")
    class ValidateDeliveryCanceled {

        @Nested
        @DisplayName("정상 케이스")
        class Success {

            @ParameterizedTest
            @ValueSource(strings = {"CANCELLED", "CREATION_BLOCKED"})
            public void 배송취소_결과가_CANCELLED_또는_CREATION_BLOCKED면_통과한다(String result) {
                //when, then
                assertThatCode(() -> orderValidator.validateDeliveryCanceled(
                        new CancelDeliveryResponse(UUID.randomUUID(), null, result)))
                        .doesNotThrowAnyException();
            }
        }

        @Nested
        @DisplayName("실패 케이스")
        class Failure {

            @ParameterizedTest
            @ValueSource(strings = {"WAITING", "CANCELED", "cancelled"})
            public void 배송취소_결과가_CANCELLED_CREATION_BLOCKED가_아니면_C409_ORDER_NOT_CANCELABLE_예외가_발생한다(String result) {
                //when, then
                assertThatThrownBy(() -> orderValidator.validateDeliveryCanceled(
                        new CancelDeliveryResponse(UUID.randomUUID(), null, result)))
                        .satisfies(e -> assertErrorCode(e, ErrorCode.C409_ORDER_NOT_CANCELABLE));
            }

            @Test
            public void 응답이_없으면_C409_ORDER_NOT_CANCELABLE_예외가_발생한다() {
                //when, then
                assertThatThrownBy(() -> orderValidator.validateDeliveryCanceled(null))
                        .satisfies(e -> assertErrorCode(e, ErrorCode.C409_ORDER_NOT_CANCELABLE));
            }
        }
    }

    @Nested
    @DisplayName("validateOrderItem()")
    class ValidateOrderItem {

        @Nested
        @DisplayName("정상 케이스")
        class Success {

            @Test
            public void 공급업체와_가격이_모두_일치하면_통과한다() {
                //given
                CreateOrderRequest request = orderRequest(requesterId, supplierId, price);
                FetchItemByIdResponse item = item(supplierId, Long.valueOf(price));

                //when, then
                assertThatCode(() -> orderValidator.validateOrderItem(item, request))
                        .doesNotThrowAnyException();
            }
        }

        @Nested
        @DisplayName("실패 케이스")
        class Failure {

            @Test
            public void 상품의_공급업체가_다르면_C400_BAD_REQUEST_예외가_발생한다() {
                //given
                CreateOrderRequest request = orderRequest(requesterId, supplierId, price);
                FetchItemByIdResponse item = item(UUID.randomUUID(), price);

                //when, then
                assertThatThrownBy(() -> orderValidator.validateOrderItem(item, request))
                        .satisfies(e -> assertErrorCode(e, ErrorCode.C400_BAD_REQUEST));
            }

            @Test
            public void 가격이_다르면_C409_ORDER_PRICE_CHANGED_예외가_발생한다() {
                //given
                CreateOrderRequest request = orderRequest(requesterId, supplierId, price);
                FetchItemByIdResponse item = item(supplierId, price + 1);

                //when, then
                assertThatThrownBy(() -> orderValidator.validateOrderItem(item, request))
                        .satisfies(e -> assertErrorCode(e, ErrorCode.C409_ORDER_PRICE_CHANGED));
            }

            @Test
            public void 공급업체와_가격이_모두_다르면_공급업체_검증이_먼저_실패한다() {
                //given
                CreateOrderRequest request = orderRequest(requesterId, supplierId, price);
                FetchItemByIdResponse item = item(UUID.randomUUID(), price + 1);

                //when, then
                assertThatThrownBy(() -> orderValidator.validateOrderItem(item, request))
                        .satisfies(e -> assertErrorCode(e, ErrorCode.C400_BAD_REQUEST));
            }
        }
    }
}

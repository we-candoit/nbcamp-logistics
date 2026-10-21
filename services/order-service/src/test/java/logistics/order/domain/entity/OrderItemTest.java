package logistics.order.domain.entity;

import logistics.order.common.exception.CustomException;
import logistics.order.common.exception.ErrorCode;
import logistics.order.domain.enums.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderItemTest {

    private static void assertErrorCode(Throwable e, ErrorCode errorCode) {
        assertThat(e).isInstanceOf(CustomException.class);
        assertThat(((CustomException) e).getErrorCode()).isEqualTo(errorCode);
    }

    private final UUID userId = UUID.randomUUID();

    //주문 상태를 지정한 주문상품 생성
    private OrderItem orderItemOf(OrderStatus status) {
        Order order = new Order(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "서울시 강남구", null);
        ReflectionTestUtils.setField(order, "status", status);
        return new OrderItem(UUID.randomUUID(), "생수 2L", 15_000L, 3L, UUID.randomUUID(), UUID.randomUUID(), order);
    }

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Nested
        @DisplayName("정상 케이스")
        class Success {

            @Test
            public void 취소된_주문의_주문상품을_삭제하면_삭제일시와_삭제자가_기록된다() {
                //given
                OrderItem orderItem = orderItemOf(OrderStatus.CANCELLED);

                //when
                orderItem.delete(userId);

                //then
                assertThat(orderItem.isDeleted()).isTrue();
                assertThat(orderItem.getDeletedAt()).isNotNull();
                assertThat(orderItem.getDeletedBy()).isEqualTo(userId);
            }

            @Test
            public void 주문상품을_삭제해도_주문은_삭제되지_않는다() {
                //given
                OrderItem orderItem = orderItemOf(OrderStatus.CANCELLED);

                //when
                orderItem.delete(userId);

                //then
                assertThat(orderItem.getOrder().isDeleted()).isFalse();
            }
        }

        @Nested
        @DisplayName("실패 케이스")
        class Failure {

            @ParameterizedTest
            @EnumSource(value = OrderStatus.class, names = "CANCELLED", mode = EnumSource.Mode.EXCLUDE)
            public void 주문이_CANCELLED가_아니면_C409_예외가_발생하고_삭제되지_않는다(OrderStatus status) {
                //given
                OrderItem orderItem = orderItemOf(status);

                //when, then
                assertThatThrownBy(() -> orderItem.delete(userId))
                        .satisfies(e -> assertErrorCode(e, ErrorCode.C409_INVALID_ORDER_STATUS));
                assertThat(orderItem.isDeleted()).isFalse();
            }
        }
    }

    @Nested
    @DisplayName("revertDelete()")
    class RevertDelete {

        @Test
        public void 삭제를_되돌리면_삭제일시와_삭제자가_비워진다() {
            //given
            OrderItem orderItem = orderItemOf(OrderStatus.CANCELLED);
            orderItem.delete(userId);

            //when
            orderItem.revertDelete();

            //then
            assertThat(orderItem.isDeleted()).isFalse();
            assertThat(orderItem.getDeletedAt()).isNull();
            assertThat(orderItem.getDeletedBy()).isNull();
        }

        @Test
        public void 삭제되지_않은_상태면_예외_없이_그대로다() {
            //given
            OrderItem orderItem = orderItemOf(OrderStatus.CANCELLED);

            //when
            orderItem.revertDelete();

            //then
            assertThat(orderItem.isDeleted()).isFalse();
        }
    }
}

package logistics.order.domain.entity;

import logistics.order.domain.enums.OrderStatus;
import logistics.order.presentation.exception.CustomException;
import logistics.order.presentation.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private static void assertErrorCode(Throwable e, ErrorCode errorCode) {
        assertThat(e).isInstanceOf(CustomException.class);
        assertThat(((CustomException) e).getErrorCode()).isEqualTo(errorCode);
    }

    private final UUID orderUserId = UUID.randomUUID();
    private final UUID requesterId = UUID.randomUUID();
    private final UUID destinationHubId = UUID.randomUUID();

    private Order createOrder() {
        return new Order(orderUserId, requesterId, destinationHubId, "서울시 강남구", "문 앞에 놓아주세요");
    }

    @Nested
    @DisplayName("생성자")
    class Constructor {

        @Nested
        @DisplayName("정상 케이스")
        class Success {

            @Test
            public void 전달한_값으로_필드가_초기화된다() {
                //when
                Order order = createOrder();

                //then
                assertThat(order.getOrderUserId()).isEqualTo(orderUserId);
                assertThat(order.getRequesterId()).isEqualTo(requesterId);
                assertThat(order.getDestinationHubId()).isEqualTo(destinationHubId);
                assertThat(order.getAddress()).isEqualTo("서울시 강남구");
                assertThat(order.getRequestNote()).isEqualTo("문 앞에 놓아주세요");
            }

            @Test
            public void 초기_상태는_WAITING이고_배송ID는_없다() {
                //when
                Order order = createOrder();

                //then
                assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.WAITING);
                assertThat(order.getDeliveryId()).isNull();
            }

            @Test
            public void 저장_전에_ID가_생성되고_주문마다_다르다() {
                //when
                Order order1 = createOrder();
                Order order2 = createOrder();

                //then
                assertThat(order1.getId()).isNotNull();
                assertThat(order2.getId()).isNotNull();
                assertThat(order1.getId()).isNotEqualTo(order2.getId());
            }

            @Test
            public void 저장_전에는_새_엔티티로_판단된다() {
                //when
                Order order = createOrder();

                //then
                //createdAt이 없으므로 save() 시 SELECT 없이 persist
                assertThat(order.isNew()).isTrue();
            }

            @Test
            public void 요청사항은_없어도_생성된다() {
                //when
                Order order = new Order(orderUserId, requesterId, destinationHubId, "서울시 강남구", null);

                //then
                assertThat(order.getRequestNote()).isNull();
                assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.WAITING);
            }
        }
    }

    @Nested
    @DisplayName("confirm()")
    class Confirm {

        @Nested
        @DisplayName("정상 케이스")
        class Success {

            @Test
            public void 배송ID가_저장되고_상태가_CONFIRMED로_변경된다() {
                //given
                Order order = createOrder();
                UUID deliveryId = UUID.randomUUID();

                //when
                order.confirm(deliveryId);

                //then
                assertThat(order.getDeliveryId()).isEqualTo(deliveryId);
                assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
            }

            @Test
            public void 확정해도_다른_필드는_변경되지_않는다() {
                //given
                Order order = createOrder();
                UUID id = order.getId();

                //when
                order.confirm(UUID.randomUUID());

                //then
                assertThat(order.getId()).isEqualTo(id);
                assertThat(order.getRequesterId()).isEqualTo(requesterId);
                assertThat(order.getDestinationHubId()).isEqualTo(destinationHubId);
            }
        }

        @Nested
        @DisplayName("실패 케이스")
        class Failure {

            @Test
            public void 배송ID가_null이면_C500_예외가_발생하고_상태는_그대로다() {
                //given
                Order order = createOrder();

                //when, then
                assertThatThrownBy(() -> order.confirm(null))
                        .satisfies(e -> assertErrorCode(e, ErrorCode.C500_INTERNAL_SERVER_ERROR));
                assertThat(order.getDeliveryId()).isNull();
                assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.WAITING);
            }

            @ParameterizedTest
            @EnumSource(value = OrderStatus.class, names = "WAITING", mode = EnumSource.Mode.EXCLUDE)
            public void WAITING이_아닌_주문을_확정하면_C409_예외가_발생하고_상태는_그대로다(OrderStatus status) {
                //given
                Order order = createOrder();
                order.setOrderStatus(status);

                //when, then
                assertThatThrownBy(() -> order.confirm(UUID.randomUUID()))
                        .satisfies(e -> assertErrorCode(e, ErrorCode.C409_INVALID_ORDER_STATUS));
                assertThat(order.getDeliveryId()).isNull();
                assertThat(order.getOrderStatus()).isEqualTo(status);
            }

            @Test
            public void 두번_확정하면_C409_예외가_발생하고_첫_배송ID가_유지된다() {
                //given
                Order order = createOrder();
                UUID first = UUID.randomUUID();
                order.confirm(first);

                //when, then
                assertThatThrownBy(() -> order.confirm(UUID.randomUUID()))
                        .satisfies(e -> assertErrorCode(e, ErrorCode.C409_INVALID_ORDER_STATUS));
                assertThat(order.getDeliveryId()).isEqualTo(first);
                assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
            }
        }
    }

    @Nested
    @DisplayName("softDelete()")
    class SoftDelete {

        @Nested
        @DisplayName("정상 케이스")
        class Success {

            @Test
            public void 생성_직후에는_삭제되지_않은_상태다() {
                //when
                Order order = createOrder();

                //then
                assertThat(order.isDeleted()).isFalse();
                assertThat(order.getDeletedAt()).isNull();
                assertThat(order.getDeletedBy()).isNull();
            }

            @Test
            public void 삭제하면_삭제일시와_삭제자가_기록된다() {
                //given
                Order order = createOrder();
                UUID userId = UUID.randomUUID();

                //when
                order.softDelete(userId);

                //then
                assertThat(order.isDeleted()).isTrue();
                assertThat(order.getDeletedAt()).isNotNull();
                assertThat(order.getDeletedBy()).isEqualTo(userId);
            }
        }
    }
}

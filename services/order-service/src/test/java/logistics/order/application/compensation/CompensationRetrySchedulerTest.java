package logistics.order.application.compensation;

import logistics.order.application.service.OrderTransactionService;
import logistics.order.common.AuthContext;
import logistics.order.domain.entity.Order;
import logistics.order.domain.entity.OrderItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class CompensationRetrySchedulerTest {

    private static final int MAX_RETRY_COUNT = 5;
    private static final int BATCH_SIZE = 50;

    private final OrderTransactionService orderTransactionService = mock(OrderTransactionService.class);
    private final OrderCompensator orderCompensator = mock(OrderCompensator.class);
    private final UUID systemUserId = UUID.randomUUID();

    //기준시각 계산을 확인하기 위해 현재 시각 고정
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 25, 12, 0);
    private final Clock clock = Clock.fixed(now.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    private final CompensationRetryScheduler scheduler = new CompensationRetryScheduler(
            orderTransactionService, orderCompensator, systemUserId, "MASTER", MAX_RETRY_COUNT, BATCH_SIZE,
            Duration.ofMinutes(10), clock);

    @Test
    public void 생성_후_10분이_지난_WAITING_주문을_전환한_뒤_재보상_대상을_조회한다() {
        //given
        InOrder inOrder = inOrder(orderTransactionService);

        //when
        scheduler.retry();

        //then
        inOrder.verify(orderTransactionService).markStaleWaitingAsCompensationFailed(now.minusMinutes(10));
        inOrder.verify(orderTransactionService).findRecompensationTargets(MAX_RETRY_COUNT, BATCH_SIZE);
    }

    @Test
    public void WAITING_주문_전환도_시스템_사용자로_실행한다() {
        //given
        List<UUID> userIds = new ArrayList<>();
        given(orderTransactionService.markStaleWaitingAsCompensationFailed(any())).willAnswer(invocation -> {
            userIds.add(AuthContext.getUserId());
            return List.of();
        });

        //when
        scheduler.retry();

        //then
        assertThat(userIds).containsExactly(systemUserId);
    }

    @Test
    public void WAITING_주문_전환에_실패해도_재보상은_진행한다() {
        //given
        OrderItem target = compensationFailedOrderItem();
        given(orderTransactionService.markStaleWaitingAsCompensationFailed(any()))
                .willThrow(new RuntimeException("DB 오류"));
        given(orderTransactionService.findRecompensationTargets(MAX_RETRY_COUNT, BATCH_SIZE))
                .willReturn(List.of(target));

        //when
        scheduler.retry();

        //then
        verify(orderCompensator).recompensateCreate(target, MAX_RETRY_COUNT);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private OrderItem compensationFailedOrderItem() {
        Order order = new Order(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "서울시 강남구", null);
        order.compensationFailed();
        return new OrderItem(UUID.randomUUID(), "생수 2L", 15_000L, 3L, UUID.randomUUID(), UUID.randomUUID(), order);
    }

    @Test
    public void 상한과_건수로_대상을_조회해_주문마다_재보상한다() {
        //given
        OrderItem first = compensationFailedOrderItem();
        OrderItem second = compensationFailedOrderItem();
        given(orderTransactionService.findRecompensationTargets(MAX_RETRY_COUNT, BATCH_SIZE))
                .willReturn(List.of(first, second));

        //when
        scheduler.retry();

        //then
        verify(orderCompensator).recompensateCreate(first, MAX_RETRY_COUNT);
        verify(orderCompensator).recompensateCreate(second, MAX_RETRY_COUNT);
    }

    @Test
    public void 요청이_없는_스레드에서도_시스템_사용자로_재보상한다() {
        //given
        RequestContextHolder.resetRequestAttributes();
        given(orderTransactionService.findRecompensationTargets(anyInt(), anyInt()))
                .willReturn(List.of(compensationFailedOrderItem()));
        List<UUID> userIds = new ArrayList<>();
        List<String> roles = new ArrayList<>();
        willAnswer(invocation -> {
            userIds.add(AuthContext.getUserId());
            roles.add(AuthContext.getUserRole());
            return null;
        }).given(orderCompensator).recompensateCreate(any(), anyInt());

        //when
        scheduler.retry();

        //then
        assertThat(userIds).containsExactly(systemUserId);
        assertThat(roles).containsExactly("MASTER");
        //실행이 끝나면 시스템 사용자 정보는 비워짐
        assertThatThrownBy(AuthContext::findUserId).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void 한_주문의_재보상에서_예외가_나도_나머지_주문은_계속_재보상한다() {
        //given
        OrderItem first = compensationFailedOrderItem();
        OrderItem second = compensationFailedOrderItem();
        given(orderTransactionService.findRecompensationTargets(MAX_RETRY_COUNT, BATCH_SIZE))
                .willReturn(List.of(first, second));
        willThrow(new RuntimeException("재보상 오류")).given(orderCompensator).recompensateCreate(first, MAX_RETRY_COUNT);

        //when
        scheduler.retry();

        //then
        verify(orderCompensator).recompensateCreate(second, MAX_RETRY_COUNT);
    }

    @Test
    public void 대상_조회에_실패하면_재보상하지_않고_예외_없이_끝난다() {
        //given
        given(orderTransactionService.findRecompensationTargets(anyInt(), anyInt()))
                .willThrow(new RuntimeException("DB 오류"));

        //when
        scheduler.retry();

        //then
        verifyNoInteractions(orderCompensator);
    }

    @Test
    public void 대상이_없으면_재보상하지_않는다() {
        //given
        given(orderTransactionService.findRecompensationTargets(anyInt(), anyInt())).willReturn(List.of());

        //when
        scheduler.retry();

        //then
        verifyNoInteractions(orderCompensator);
    }
}

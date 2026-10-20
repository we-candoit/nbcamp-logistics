package logistics.order.application.service;

import jakarta.persistence.EntityManager;
import logistics.order.application.validator.OrderValidator;
import logistics.order.common.AuthHeaders;
import logistics.order.config.AuditingConfig;
import logistics.order.domain.entity.Order;
import logistics.order.domain.entity.OrderItem;
import logistics.order.domain.enums.OrderStatus;
import logistics.order.infrastructure.AuditorAwareImpl;
import logistics.order.infrastructure.repository.OrderItemRepository;
import logistics.order.infrastructure.repository.OrderRepository;
import logistics.order.common.exception.CustomException;
import logistics.order.common.exception.ErrorCode;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

//H2 인메모리 DB로 JPA 계층만 띄워서 실제 저장 동작 확인
@DataJpaTest(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
@Import({OrderTransactionService.class, AuditingConfig.class, OrderTransactionServiceTest.AuditorConfig.class})
class OrderTransactionServiceTest {

    //AuditingConfig가 auditorAwareRef = "auditorAwareImpl" 이름으로 빈을 찾으므로 같은 이름으로 등록
    @TestConfiguration
    static class AuditorConfig {
        @Bean
        AuditorAwareImpl auditorAwareImpl() {
            return new AuditorAwareImpl();
        }

        //취소/삭제 시 권한 검증은 실제 검증기로 확인
        @Bean
        OrderValidator orderValidator() {
            return new OrderValidator();
        }
    }

    @Autowired
    private OrderTransactionService orderTransactionService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private EntityManager em;

    private final UUID userId = UUID.randomUUID();
    private MockHttpServletRequest httpRequest;

    @BeforeEach
    void setUp() {
        //AuditorAwareImpl이 요청 속성에서 userId를 읽으므로 요청 컨텍스트 세팅
        httpRequest = new MockHttpServletRequest();
        httpRequest.setAttribute(AuthHeaders.USER_ID, userId.toString());
        //취소 시 권한 검증(AuthContext.isMaster)에 role이 필요 / 기본은 일반 유저
        httpRequest.setAttribute(AuthHeaders.ROLE, "COMPANY_MANAGER");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(httpRequest));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private Order confirmedOrder() {
        Order order = new Order(userId, UUID.randomUUID(), UUID.randomUUID(), "서울시 강남구", "문 앞에 놓아주세요");
        order.confirm(UUID.randomUUID());
        return order;
    }

    private OrderItem orderItem(Order order) {
        return new OrderItem(UUID.randomUUID(), "생수 2L", 15_000L, 3L,
                UUID.randomUUID(), UUID.randomUUID(), order);
    }

    @Nested
    @DisplayName("정상 케이스")
    class Success {

        @Test
        public void 주문과_주문상품이_함께_저장된다() {
            //given
            Order order = confirmedOrder();
            OrderItem orderItem = orderItem(order);

            //when
            orderTransactionService.save(order, orderItem);
            em.flush();
            em.clear();

            //then
            Order savedOrder = orderRepository.findById(order.getId()).orElseThrow();
            OrderItem savedItem = orderItemRepository.findById(orderItem.getId()).orElseThrow();

            assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(savedOrder.getDeliveryId()).isEqualTo(order.getDeliveryId());
            assertThat(savedItem.getOrder().getId()).isEqualTo(order.getId());
            assertThat(savedItem.getQuantity()).isEqualTo(3L);
        }

        @Test
        public void 저장하면_Auditing_필드가_채워진다() {
            //given
            Order order = confirmedOrder();
            OrderItem orderItem = orderItem(order);

            //when
            orderTransactionService.save(order, orderItem);
            em.flush();

            //then
            assertThat(order.getCreatedAt()).isNotNull();
            assertThat(order.getUpdatedAt()).isNotNull();
            assertThat(order.getCreatedBy()).isEqualTo(userId);
            assertThat(order.getUpdatedBy()).isEqualTo(userId);
            assertThat(orderItem.getCreatedBy()).isEqualTo(userId);
        }

        @Test
        public void 새_엔티티는_merge가_아닌_persist로_저장되어_원본_객체가_관리된다() {
            //given
            Order order = confirmedOrder();
            OrderItem orderItem = orderItem(order);

            //when
            orderTransactionService.save(order, orderItem);

            //then
            //persist → 원본 객체가 영속 상태 / merge였다면 복사본이 관리되고 원본은 비영속
            assertThat(em.contains(order)).isTrue();
            assertThat(em.contains(orderItem)).isTrue();
            //OrderService 응답의 createdAt이 원본 객체에서 읽히는 근거
            assertThat(order.getCreatedAt()).isNotNull();
            assertThat(order.isNew()).isFalse();
        }

        @Test
        public void 주문상태는_문자열로_저장된다() {
            //given
            Order order = confirmedOrder();

            //when
            orderTransactionService.save(order, orderItem(order));
            em.flush();

            //then
            Object orderStatus = em.createNativeQuery("select status from p_orders where id = :id")
                    .setParameter("id", order.getId())
                    .getSingleResult();

            assertThat(orderStatus).isEqualTo("CONFIRMED");
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class Failure {

        @Test
        public void 확정되지_않은_주문은_배송ID가_없어_저장에_실패한다() {
            //given
            Order order = new Order(userId, UUID.randomUUID(), UUID.randomUUID(), "서울시 강남구", null);

            //when, then
            //deliveryId nullable = false
            assertThatThrownBy(() -> {
                orderTransactionService.save(order, orderItem(order));
                em.flush();
            }).rootCause().hasMessageContaining("NULL not allowed").hasMessageContaining("DELIVERY_ID");
        }

        @Test
        public void 요청에_userId가_없으면_생성자_정보가_없어_저장에_실패한다() {
            //given
            httpRequest.removeAttribute(AuthHeaders.USER_ID);
            Order order = confirmedOrder();

            //when, then
            //createdBy nullable = false
            assertThatThrownBy(() -> {
                orderTransactionService.save(order, orderItem(order));
                em.flush();
            }).rootCause().hasMessageContaining("NULL not allowed").hasMessageContaining("CREATED_BY");
        }

        @Test
        public void 같은_주문에_주문상품을_두번_저장하면_실패한다() {
            //given
            Order order = confirmedOrder();
            orderTransactionService.save(order, orderItem(order));
            em.flush();

            //when, then
            //order_id unique (주문 1개 = 주문상품 1개)
            assertThatThrownBy(() -> {
                orderItemRepository.save(orderItem(order));
                em.flush();
            }).rootCause().hasMessageContaining("Unique index or primary key violation");
        }
    }

    @Nested
    @DisplayName("findWithOrder()")
    class FindWithOrder {

        @Test
        public void 주문ID로_주문상품과_주문을_함께_조회한다() {
            //given
            Order order = confirmedOrder();
            OrderItem orderItem = orderItem(order);
            orderTransactionService.save(order, orderItem);
            em.flush();
            em.clear();

            //when
            OrderItem found = orderTransactionService.findWithOrder(order.getId());

            //then
            assertThat(found.getId()).isEqualTo(orderItem.getId());
            //fetch join → 주문이 프록시가 아닌 실제 엔티티로 로딩됨
            assertThat(Hibernate.isInitialized(found.getOrder())).isTrue();
            assertThat(found.getOrder().getId()).isEqualTo(order.getId());
        }

        @Test
        public void 없는_주문이면_C404_예외가_발생한다() {
            //when, then
            assertThatThrownBy(() -> orderTransactionService.findWithOrder(UUID.randomUUID()))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.C404_ORDER_NOT_FOUND));
        }

        @Test
        public void 삭제된_주문이면_C404_예외가_발생한다() {
            //given
            Order order = confirmedOrder();
            orderTransactionService.save(order, orderItem(order));
            //삭제는 취소된 주문만 가능 → 취소 후 삭제
            order.cancel();
            order.delete(userId);
            em.flush();
            em.clear();

            //when, then
            assertThatThrownBy(() -> orderTransactionService.findWithOrder(order.getId()))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.C404_ORDER_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("cancel()")
    class Cancel {

        @Test
        public void 주문을_취소하면_DB에_CANCELLED로_반영된다() {
            //given
            Order order = confirmedOrder();
            orderTransactionService.save(order, orderItem(order));
            em.flush();
            em.clear();

            //when
            Order canceled = orderTransactionService.cancel(order.getId());
            em.flush();
            em.clear();

            //then
            assertThat(canceled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        public void 취소해도_배송ID는_유지된다() {
            //given
            Order order = confirmedOrder();
            orderTransactionService.save(order, orderItem(order));
            em.flush();
            em.clear();

            //when
            orderTransactionService.cancel(order.getId());
            em.flush();
            em.clear();

            //then
            assertThat(orderRepository.findById(order.getId()).orElseThrow().getDeliveryId())
                    .isEqualTo(order.getDeliveryId());
        }

        @Test
        public void 없는_주문이면_C404_예외가_발생한다() {
            //when, then
            assertThatThrownBy(() -> orderTransactionService.cancel(UUID.randomUUID()))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.C404_ORDER_NOT_FOUND));
        }

        @Test
        public void 삭제된_주문이면_C404_예외가_발생한다() {
            //given
            Order order = confirmedOrder();
            orderTransactionService.save(order, orderItem(order));
            //삭제는 취소된 주문만 가능 → 취소 후 삭제
            order.cancel();
            order.delete(userId);
            em.flush();
            em.clear();

            //when, then
            assertThatThrownBy(() -> orderTransactionService.cancel(order.getId()))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.C404_ORDER_NOT_FOUND));
        }

        @Test
        public void MASTER는_다른_사람의_주문도_취소한다() {
            //given
            httpRequest.setAttribute(AuthHeaders.ROLE, "MASTER");
            Order order = new Order(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "서울시 강남구", null);
            order.confirm(UUID.randomUUID());
            orderTransactionService.save(order, orderItem(order));
            em.flush();
            em.clear();

            //when
            orderTransactionService.cancel(order.getId());
            em.flush();
            em.clear();

            //then
            assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        public void 다른_사람의_주문이면_C403_예외가_발생하고_상태는_그대로다() {
            //given
            Order order = new Order(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "서울시 강남구", null);
            order.confirm(UUID.randomUUID());
            orderTransactionService.save(order, orderItem(order));
            em.flush();
            em.clear();

            //when, then
            assertThatThrownBy(() -> orderTransactionService.cancel(order.getId()))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.C403_FORBIDDEN_ORDER));
            em.flush();
            em.clear();
            assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.CONFIRMED);
        }

        @Test
        public void 이미_취소된_주문이면_C409_예외가_발생한다() {
            //given
            Order order = confirmedOrder();
            orderTransactionService.save(order, orderItem(order));
            em.flush();
            em.clear();
            orderTransactionService.cancel(order.getId());
            em.flush();
            em.clear();

            //when, then
            assertThatThrownBy(() -> orderTransactionService.cancel(order.getId()))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.C409_INVALID_ORDER_STATUS));
        }
    }

    @Nested
    @DisplayName("revertCancel()")
    class RevertCancel {

        @Test
        public void 취소된_주문을_CONFIRMED로_복구한다() {
            //given
            Order order = confirmedOrder();
            orderTransactionService.save(order, orderItem(order));
            em.flush();
            em.clear();
            orderTransactionService.cancel(order.getId());
            em.flush();
            em.clear();

            //when
            orderTransactionService.revertCancel(order.getId());
            em.flush();
            em.clear();

            //then
            assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.CONFIRMED);
        }

        @Test
        public void 없는_주문이면_C404_예외가_발생한다() {
            //when, then
            assertThatThrownBy(() -> orderTransactionService.revertCancel(UUID.randomUUID()))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.C404_ORDER_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("delete()")
    class Delete {

        //취소된 주문을 저장 후 영속성 컨텍스트 초기화
        private Order savedCancelledOrder(UUID orderUserId) {
            Order order = new Order(orderUserId, UUID.randomUUID(), UUID.randomUUID(), "서울시 강남구", null);
            order.confirm(UUID.randomUUID());
            order.cancel();
            orderTransactionService.save(order, orderItem(order));
            em.flush();
            em.clear();
            return order;
        }

        private OrderItem findOrderItem(UUID orderId) {
            return orderItemRepository.findWithOrderIncludingDeleted(orderId).orElseThrow();
        }

        @Test
        public void 주문을_삭제하면_주문과_주문상품이_함께_삭제된다() {
            //given
            Order order = savedCancelledOrder(userId);

            //when
            Order deleted = orderTransactionService.delete(order.getId(), userId);
            em.flush();
            em.clear();

            //then
            assertThat(deleted.getDeliveryId()).isEqualTo(order.getDeliveryId());
            OrderItem found = findOrderItem(order.getId());
            assertThat(found.isDeleted()).isTrue();
            assertThat(found.getDeletedBy()).isEqualTo(userId);
            assertThat(found.getOrder().isDeleted()).isTrue();
            assertThat(found.getOrder().getDeletedBy()).isEqualTo(userId);
        }

        @Test
        public void 삭제된_주문은_조회되지_않는다() {
            //given
            Order order = savedCancelledOrder(userId);
            orderTransactionService.delete(order.getId(), userId);
            em.flush();
            em.clear();

            //when, then
            assertThatThrownBy(() -> orderTransactionService.findWithOrder(order.getId()))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.C404_ORDER_NOT_FOUND));
        }

        @Test
        public void MASTER는_다른_사람의_주문도_삭제한다() {
            //given
            httpRequest.setAttribute(AuthHeaders.ROLE, "MASTER");
            Order order = savedCancelledOrder(UUID.randomUUID());

            //when
            orderTransactionService.delete(order.getId(), userId);
            em.flush();
            em.clear();

            //then
            assertThat(findOrderItem(order.getId()).getOrder().isDeleted()).isTrue();
        }

        @Test
        public void 없는_주문이면_C404_예외가_발생한다() {
            //when, then
            assertThatThrownBy(() -> orderTransactionService.delete(UUID.randomUUID(), userId))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.C404_ORDER_NOT_FOUND));
        }

        @Test
        public void 이미_삭제된_주문이면_C404_예외가_발생한다() {
            //given
            Order order = savedCancelledOrder(userId);
            orderTransactionService.delete(order.getId(), userId);
            em.flush();
            em.clear();

            //when, then
            assertThatThrownBy(() -> orderTransactionService.delete(order.getId(), userId))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.C404_ORDER_NOT_FOUND));
        }

        @Test
        public void 다른_사람의_주문이면_C403_예외가_발생하고_삭제되지_않는다() {
            //given
            Order order = savedCancelledOrder(UUID.randomUUID());

            //when, then
            assertThatThrownBy(() -> orderTransactionService.delete(order.getId(), userId))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.C403_FORBIDDEN_ORDER));
            em.flush();
            em.clear();
            assertThat(findOrderItem(order.getId()).getOrder().isDeleted()).isFalse();
        }

        @Test
        public void 취소되지_않은_주문이면_C409_예외가_발생하고_삭제되지_않는다() {
            //given
            Order order = confirmedOrder();
            orderTransactionService.save(order, orderItem(order));
            em.flush();
            em.clear();

            //when, then
            assertThatThrownBy(() -> orderTransactionService.delete(order.getId(), userId))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.C409_INVALID_ORDER_STATUS));
            em.flush();
            em.clear();
            OrderItem found = findOrderItem(order.getId());
            assertThat(found.isDeleted()).isFalse();
            assertThat(found.getOrder().isDeleted()).isFalse();
        }
    }

    @Nested
    @DisplayName("revertDelete()")
    class RevertDelete {

        @Test
        public void 삭제된_주문과_주문상품을_복구한다() {
            //given
            Order order = confirmedOrder();
            order.cancel();
            orderTransactionService.save(order, orderItem(order));
            em.flush();
            em.clear();
            orderTransactionService.delete(order.getId(), userId);
            em.flush();
            em.clear();

            //when
            orderTransactionService.revertDelete(order.getId());
            em.flush();
            em.clear();

            //then
            OrderItem found = orderTransactionService.findWithOrder(order.getId());
            assertThat(found.isDeleted()).isFalse();
            assertThat(found.getDeletedBy()).isNull();
            assertThat(found.getOrder().isDeleted()).isFalse();
            assertThat(found.getOrder().getDeletedBy()).isNull();
        }

        @Test
        public void 없는_주문이면_C404_예외가_발생한다() {
            //when, then
            assertThatThrownBy(() -> orderTransactionService.revertDelete(UUID.randomUUID()))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.C404_ORDER_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("findAllWithOrder()")
    class FindOrderItemsWithOrder {

        private final UUID otherUserId = UUID.randomUUID();
        private Order myConfirmed;
        private Order myWaiting;
        private Order otherConfirmed;

        //내 주문(CONFIRMED, WAITING) + 다른 유저 주문(CONFIRMED) + 삭제된 주문
        @BeforeEach
        void setUpOrders() {
            myConfirmed = orderOf(userId, true);
            myWaiting = orderOf(userId, false);
            otherConfirmed = orderOf(otherUserId, true);

            //삭제는 취소된 주문만 가능 → 취소 후 삭제
            Order deleted = orderOf(userId, true);
            deleted.cancel();
            deleted.delete(userId);

            em.flush();
            em.clear();
        }

        //confirm 여부로 상태 결정 / WAITING은 deliveryId가 없어 DB 저장 불가 → 테스트에서만 세팅
        private Order orderOf(UUID orderUserId, boolean confirmed) {
            Order order = new Order(orderUserId, UUID.randomUUID(), UUID.randomUUID(), "서울시 강남구", null);
            order.confirm(UUID.randomUUID());
            if (!confirmed) {
                ReflectionTestUtils.setField(order, "status", OrderStatus.WAITING);
            }
            orderTransactionService.save(order, orderItem(order));
            return order;
        }

        private List<UUID> orderIds(List<OrderItem> orderItems) {
            return orderItems.stream().map(orderItem -> orderItem.getOrder().getId()).toList();
        }

        @Nested
        @DisplayName("정렬")
        class Sorting {

            private Order myConfirmedLatest;

            //생성시각이 거의 같아 순서가 불안정 → DB의 created_at을 날짜로 직접 지정
            //myConfirmed(10/01) < myWaiting(10/02) < otherConfirmed(10/03) < myConfirmedLatest(10/04)
            @BeforeEach
            void setUpCreatedAt() {
                myConfirmedLatest = orderOf(userId, true);
                em.flush();

                setCreatedAt(myConfirmed, LocalDateTime.of(2026, 10, 1, 12, 0));
                setCreatedAt(myWaiting, LocalDateTime.of(2026, 10, 2, 12, 0));
                setCreatedAt(otherConfirmed, LocalDateTime.of(2026, 10, 3, 12, 0));
                setCreatedAt(myConfirmedLatest, LocalDateTime.of(2026, 10, 4, 12, 0));
                em.clear();
            }

            //createdAt은 updatable = false라 엔티티로는 변경 불가 → native query로 변경
            private void setCreatedAt(Order order, LocalDateTime createdAt) {
                em.createNativeQuery("update p_orders set created_at = :createdAt where id = :id")
                        .setParameter("createdAt", createdAt)
                        .setParameter("id", order.getId())
                        .executeUpdate();
            }

            @Test
            public void 전체_주문은_최신순으로_조회된다() {
                //when
                List<OrderItem> result = orderTransactionService.findAllWithOrder(null, null);

                //then
                assertThat(orderIds(result)).containsExactly(
                        myConfirmedLatest.getId(), otherConfirmed.getId(), myWaiting.getId(), myConfirmed.getId());
            }

            @Test
            public void 상태별_주문은_최신순으로_조회된다() {
                //when
                List<OrderItem> result = orderTransactionService.findAllWithOrder(null, OrderStatus.CONFIRMED);

                //then
                assertThat(orderIds(result)).containsExactly(
                        myConfirmedLatest.getId(), otherConfirmed.getId(), myConfirmed.getId());
            }

            @Test
            public void 유저별_주문은_최신순으로_조회된다() {
                //when
                List<OrderItem> result = orderTransactionService.findAllWithOrder(userId, null);

                //then
                assertThat(orderIds(result)).containsExactly(
                        myConfirmedLatest.getId(), myWaiting.getId(), myConfirmed.getId());
            }

            @Test
            public void 유저와_상태별_주문은_최신순으로_조회된다() {
                //when
                List<OrderItem> result = orderTransactionService.findAllWithOrder(userId, OrderStatus.CONFIRMED);

                //then
                assertThat(orderIds(result)).containsExactly(myConfirmedLatest.getId(), myConfirmed.getId());
            }
        }

        @Test
        public void 조건이_없으면_삭제되지_않은_전체_주문을_조회한다() {
            //when
            List<OrderItem> result = orderTransactionService.findAllWithOrder(null, null);

            //then
            assertThat(orderIds(result))
                    .containsExactlyInAnyOrder(myConfirmed.getId(), myWaiting.getId(), otherConfirmed.getId());
        }

        @Test
        public void 상태만_지정하면_해당_상태의_전체_주문을_조회한다() {
            //when
            List<OrderItem> result = orderTransactionService.findAllWithOrder(null, OrderStatus.CONFIRMED);

            //then
            assertThat(orderIds(result)).containsExactlyInAnyOrder(myConfirmed.getId(), otherConfirmed.getId());
        }

        @Test
        public void 유저만_지정하면_해당_유저의_주문만_조회한다() {
            //when
            List<OrderItem> result = orderTransactionService.findAllWithOrder(userId, null);

            //then
            assertThat(orderIds(result)).containsExactlyInAnyOrder(myConfirmed.getId(), myWaiting.getId());
        }

        @Test
        public void 유저와_상태를_지정하면_해당_유저의_해당_상태_주문만_조회한다() {
            //when
            List<OrderItem> result = orderTransactionService.findAllWithOrder(userId, OrderStatus.WAITING);

            //then
            assertThat(orderIds(result)).containsExactly(myWaiting.getId());
        }

        @Test
        public void 조회된_주문상품의_주문은_fetch_join으로_함께_로딩된다() {
            //when
            List<OrderItem> result = orderTransactionService.findAllWithOrder(null, null);

            //then
            assertThat(result).allMatch(orderItem -> Hibernate.isInitialized(orderItem.getOrder()));
        }

        @Test
        public void 조건에_맞는_주문이_없으면_빈_목록을_반환한다() {
            //when
            List<OrderItem> result = orderTransactionService.findAllWithOrder(otherUserId, OrderStatus.WAITING);

            //then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("트랜잭션")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    class TransactionBoundary {

        //테스트 트랜잭션 없이 실행 → save()의 @Transactional이 직접 커밋/롤백
        @AfterEach
        void cleanUp() {
            orderItemRepository.deleteAll();
            orderRepository.deleteAll();
        }

        @Test
        public void 정상_저장하면_커밋되어_다른_트랜잭션에서_조회된다() {
            //given
            Order order = confirmedOrder();

            //when
            orderTransactionService.save(order, orderItem(order));

            //then
            assertThat(orderRepository.count()).isEqualTo(1);
            assertThat(orderItemRepository.count()).isEqualTo(1);
        }

        @Test
        public void 주문상품_저장에_실패하면_주문도_함께_롤백된다() {
            //given
            Order order = confirmedOrder();
            //itemName nullable = false 위반
            OrderItem invalidItem = new OrderItem(UUID.randomUUID(), null, 15_000L, 3L,
                    UUID.randomUUID(), UUID.randomUUID(), order);

            //when
            assertThatThrownBy(() -> orderTransactionService.save(order, invalidItem))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .rootCause().hasMessageContaining("ITEM_NAME");

            //then
            assertThat(orderRepository.count()).isZero();
            assertThat(orderItemRepository.count()).isZero();
        }
    }
}

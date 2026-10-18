package logistics.order.application.service;

import jakarta.persistence.EntityManager;
import logistics.order.common.AuthHeaders;
import logistics.order.config.AuditingConfig;
import logistics.order.domain.entity.Order;
import logistics.order.domain.entity.OrderItem;
import logistics.order.domain.enums.OrderStatus;
import logistics.order.infrastructure.AuditorAwareImpl;
import logistics.order.infrastructure.repository.OrderItemRepository;
import logistics.order.infrastructure.repository.OrderRepository;
import logistics.order.presentation.exception.CustomException;
import logistics.order.presentation.exception.ErrorCode;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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

            assertThat(savedOrder.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
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
            Object orderStatus = em.createNativeQuery("select order_status from p_order where id = :id")
                    .setParameter("id", order.getId())
                    .getSingleResult();
            Object orderItemStatus = em.createNativeQuery("select order_item_status from p_order_item where order_id = :id")
                    .setParameter("id", order.getId())
                    .getSingleResult();

            assertThat(orderStatus).isEqualTo("CONFIRMED");
            assertThat(orderItemStatus).isEqualTo("ORDERED");
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
    @DisplayName("findOrderItemWithOrder()")
    class FindOrderItemWithOrder {

        @Test
        public void 주문ID로_주문상품과_주문을_함께_조회한다() {
            //given
            Order order = confirmedOrder();
            OrderItem orderItem = orderItem(order);
            orderTransactionService.save(order, orderItem);
            em.flush();
            em.clear();

            //when
            OrderItem found = orderTransactionService.findOrderItemWithOrder(order.getId());

            //then
            assertThat(found.getId()).isEqualTo(orderItem.getId());
            //fetch join → 주문이 프록시가 아닌 실제 엔티티로 로딩됨
            assertThat(Hibernate.isInitialized(found.getOrder())).isTrue();
            assertThat(found.getOrder().getId()).isEqualTo(order.getId());
        }

        @Test
        public void 없는_주문이면_C404_예외가_발생한다() {
            //when, then
            assertThatThrownBy(() -> orderTransactionService.findOrderItemWithOrder(UUID.randomUUID()))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.C404_ORDER_NOT_FOUND));
        }

        @Test
        public void 삭제된_주문이면_C404_예외가_발생한다() {
            //given
            Order order = confirmedOrder();
            orderTransactionService.save(order, orderItem(order));
            order.softDelete(userId);
            em.flush();
            em.clear();

            //when, then
            assertThatThrownBy(() -> orderTransactionService.findOrderItemWithOrder(order.getId()))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                            .isEqualTo(ErrorCode.C404_ORDER_NOT_FOUND));
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

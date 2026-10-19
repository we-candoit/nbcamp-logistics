package logistics.order.application.service;

import logistics.order.application.validator.OrderValidator;
import logistics.order.domain.entity.Order;
import logistics.order.domain.entity.OrderItem;
import logistics.order.domain.enums.OrderStatus;
import logistics.order.infrastructure.repository.OrderItemRepository;
import logistics.order.infrastructure.repository.OrderRepository;
import logistics.order.common.exception.CustomException;
import logistics.order.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

//주문 DB 트랜잭션 처리
//상태 변경 메서드는 조회 → 검증(권한/상태) → 변경을 한 트랜잭션 안에서 처리
//조회 메서드는 데이터만 반환하고, 권한 검증은 호출하는 쪽(OrderService)에서 처리
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrderTransactionService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderValidator orderValidator;

    //주문ID를 사용하여 주문 및 주문상품 단건조회
    public OrderItem findWithOrder(UUID orderId) {
        return orderItemRepository.findWithOrder(orderId)
                .orElseThrow(() -> new CustomException(ErrorCode.C404_ORDER_NOT_FOUND));
    }

    //유저ID, 주문상태를 사용하여 주문 및 주문상품 목록조회
    public List<OrderItem> findAllWithOrder(UUID orderUserId, OrderStatus orderStatus) {
        if (orderStatus == null) {
            return orderUserId == null
                    ? orderItemRepository.findAllWithOrder()
                    : orderItemRepository.findAllWithOrderByOrderUserId(orderUserId);
        }

        return orderUserId == null
                ? orderItemRepository.findAllWithOrderByStatus(orderStatus)
                : orderItemRepository.findAllWithOrderByOrderUserIdAndStatus(orderUserId, orderStatus);
    }

    //주문 및 주문상품 저장
    @Transactional
    public void save(Order order, OrderItem orderItems) {
        orderRepository.save(order);
        orderItemRepository.save(orderItems);
    }

    //주문ID를 사용하여 특정 주문취소
    @Transactional
    public Order cancel(UUID orderId) {
        Order order = orderRepository.findByIdAndDeletedAtIsNull(orderId).orElseThrow(
                () -> new CustomException(ErrorCode.C404_ORDER_NOT_FOUND));

        orderValidator.validateOrderOwner(order.getOrderUserId());
        order.cancel();
        return order;
    }

    //주문취소 실패시 보상 시나리오
    @Transactional
    public void compensateCancelOrder(UUID orderId) {
        Order order = orderRepository.findByIdAndDeletedAtIsNull(orderId).orElseThrow(
                () -> new CustomException(ErrorCode.C404_ORDER_NOT_FOUND));

        order.revertCancel();
    }
}

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

    //주문ID를 사용하여 특정 주문삭제, 주문과 주문상품을 함께 soft delete
    @Transactional
    public Order delete(UUID orderId, UUID userId) {
        OrderItem orderItem = orderItemRepository.findWithOrder(orderId).orElseThrow(
                () -> new CustomException(ErrorCode.C404_ORDER_NOT_FOUND));
        Order order = orderItem.getOrder();

        orderValidator.validateOrderOwner(order.getOrderUserId());
        order.delete(userId);
        orderItem.delete(userId);
        return order;
    }

    //주문생성 실패시 보상 결과에 따라 주문상태 변경, 보상 성공 FAILED, 보상 실패 COMPENSATION_FAILED
    @Transactional
    public void failCreate(UUID orderId, boolean compensated) {
        Order order = orderRepository.findByIdAndDeletedAtIsNull(orderId).orElseThrow(
                () -> new CustomException(ErrorCode.C404_ORDER_NOT_FOUND));

        if (compensated) {
            order.failed();
        } else {
            order.compensationFailed();
        }
    }

    //주문ID를 사용하여 주문과 주문상품 삭제 되돌림, 삭제된 주문도 조회
    @Transactional
    public void revertDelete(UUID orderId) {
        OrderItem orderItem = orderItemRepository.findWithOrderIncludingDeleted(orderId).orElseThrow(
                () -> new CustomException(ErrorCode.C404_ORDER_NOT_FOUND));

        orderItem.getOrder().revertDelete();
        orderItem.revertDelete();
    }

    //주문ID를 사용하여 주문취소 되돌림, CONFIRMED 상태로 복구
    @Transactional
    public void revertCancel(UUID orderId) {
        Order order = orderRepository.findByIdAndDeletedAtIsNull(orderId).orElseThrow(
                () -> new CustomException(ErrorCode.C404_ORDER_NOT_FOUND));

        order.revertCancel();
    }
}

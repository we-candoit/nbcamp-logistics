package logistics.order.application.service;

import logistics.order.domain.entity.Order;
import logistics.order.domain.entity.OrderItem;
import logistics.order.domain.enums.OrderStatus;
import logistics.order.infrastructure.repository.OrderItemRepository;
import logistics.order.infrastructure.repository.OrderRepository;
import logistics.order.presentation.exception.CustomException;
import logistics.order.presentation.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;


@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrderTransactionService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    @Transactional
    public void save(Order order, OrderItem orderItems) {
        orderRepository.save(order);
        orderItemRepository.save(orderItems);
    }

    //주문 단건 조회 / 삭제된 주문은 없는 주문으로 처리
    public OrderItem findOrderItemWithOrder(UUID orderId) {
        return orderItemRepository.findWithOrderByOrderId(orderId)
                .filter(orderItem -> !orderItem.getOrder().isDeleted())
                .orElseThrow(() -> new CustomException(ErrorCode.C404_ORDER_NOT_FOUND));
    }

    //주문 목록 조회 / orderUserId, status가 null이면 해당 조건 없이 조회
    public List<OrderItem> findOrderItemsWithOrder(UUID orderUserId, OrderStatus status) {
        if (orderUserId == null) {
            return status == null
                    ? orderItemRepository.findAllWithOrder()
                    : orderItemRepository.findAllWithOrderByStatus(status);
        }

        return status == null
                ? orderItemRepository.findAllWithOrderByOrderUserId(orderUserId)
                : orderItemRepository.findAllWithOrderByOrderUserIdAndStatus(orderUserId, status);
    }
}

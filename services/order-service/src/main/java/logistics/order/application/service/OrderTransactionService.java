package logistics.order.application.service;

import jakarta.validation.constraints.NotNull;
import logistics.order.application.validator.OrderValidator;
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
    private final OrderValidator orderValidator;

    //주문ID를 사용하여 주문 및 주문상품 단건조회
    public OrderItem findWithOrder(UUID orderId) {
        return orderItemRepository.findWithOrder(orderId)
                .orElseThrow(() -> new CustomException(ErrorCode.C404_ORDER_NOT_FOUND));
    }

    //유저ID, 주문상태를 사용하여 주문 및 주문상품 목록조회
    public List<OrderItem> findAllWithOrder(UUID orderUserId, String status) {
        if (status == null) {
            return orderUserId == null
                    ? orderItemRepository.findAllWithOrder()
                    : orderItemRepository.findAllWithOrder(orderUserId);
        }
        OrderStatus orderStatus = OrderStatus.valueOf(status);

        return orderUserId == null
                ? orderItemRepository.findAllWithOrder(orderStatus)
                : orderItemRepository.findAllWithOrder(orderUserId, orderStatus);
    }

    //주문 및 주문상품 저장
    @Transactional
    public void save(Order order, OrderItem orderItems) {
        orderRepository.save(order);
        orderItemRepository.save(orderItems);
    }

    //주문ID를 사용하여 특정 주문취소
    @NotNull
    @Transactional
    public Order cancel(UUID orderId) {
        Order order = orderRepository.findByIdAndDeletedAtIsNull(orderId).orElseThrow(
                () -> new CustomException(ErrorCode.C404_ORDER_NOT_FOUND));

        orderValidator.validateOrderOwner(order.getOrderUserId());
        orderValidator.validateCancelable(order);
        return order.cancel();
    }

    //주문취소 실패시 보상 시나리오
    @Transactional
    public void compensateCancelOrder(UUID orderId) {
        Order order = orderRepository.findByIdAndDeletedAtIsNull(orderId).orElseThrow(
                () -> new CustomException(ErrorCode.C404_ORDER_NOT_FOUND));

        order.revertCancel();
    }
}

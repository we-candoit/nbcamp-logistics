package logistics.order.application.service;

import logistics.order.application.compensation.OrderCompensator;
import logistics.order.application.compensation.CreateOrderProgress;
import logistics.order.application.validator.OrderValidator;
import logistics.order.common.AuthContext;
import logistics.order.domain.entity.Order;
import logistics.order.domain.entity.OrderItem;
import logistics.order.domain.enums.OrderStatus;
import logistics.order.infrastructure.client.dto.*;
import logistics.order.presentation.dto.request.CreateOrderRequest;
import logistics.order.presentation.dto.response.CancelOrderResponse;
import logistics.order.presentation.dto.response.CreateOrderResponse;
import logistics.order.presentation.dto.response.OrderDetailResponse;
import logistics.order.presentation.dto.response.OrderListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderValidator orderValidator;
    private final OrderTransactionService orderTransactionService;
    private final ClientService clientService;
    private final OrderCompensator orderCompensator;

    //주문생성
    public CreateOrderResponse create(CreateOrderRequest request) {

        FetchUserByIdResponse user = clientService.fetchUserById(AuthContext.getUserId());
        OrderItem orderItem = prepareCreateOrder(request, user);
        Order order = orderItem.getOrder();
        CreateOrderProgress progress = CreateOrderProgress.start(orderItem);

        try {
            //재고감소
            clientService.decreaseStock(orderItem.getDepartureHubId(), orderItem.getItemId(), orderItem.getQuantity());
            progress.markStockDecreased();

            //배송생성
            CreateDeliveryResponse delivery = clientService.createDelivery(
                    CreateDeliveryRequest.of(order, orderItem, request.getRecipientName(), request.getRecipientPhone())
            );
            progress.markDeliveryCreated(delivery.getDeliveryId());

            //주문확정 / CONFIRMED 상태
            order.confirm(delivery.getDeliveryId());
            orderTransactionService.save(order, orderItem);
        } catch (RuntimeException e) {
            orderCompensator.compensateCreate(progress);
            throw e;
        }

        //Slack알림

        return CreateOrderResponse.from(orderItem);
    }

    //주문ID로 주문 단건조회
    public OrderDetailResponse getOrderById(UUID orderId) {
        OrderItem orderItem = orderTransactionService.findWithOrder(orderId);
        orderValidator.validateOrderOwner(orderItem.getOrder().getOrderUserId());

        return OrderDetailResponse.from(orderItem);
    }

    //STATUS에 맞는 주문 목록조회, 만약 STATUS 비어 있으면 주문 전체조회
    public OrderListResponse getOrdersByStatus(OrderStatus orderStatus) {
        //MASTER는 모든 주문, 그 외는 본인이 생성한 주문만
        UUID orderUserId = AuthContext.isMaster() ? null : AuthContext.getUserId();

        return new OrderListResponse(orderTransactionService.findAllWithOrder(orderUserId, orderStatus)
                .stream().map(OrderDetailResponse::from)
                .toList()
        );
    }

    //주문ID로 주문취소
    public CancelOrderResponse cancelOrderById(UUID orderId) {
        Order order = orderTransactionService.cancel(orderId);

        try {
            clientService.cancelDelivery(order.getDeliveryId());
        } catch (RuntimeException e) {
            //배송취소 실패시 주문상태 CONFIRMED로 복구하는 보상시나리오
            orderCompensator.compensateCancel(orderId);
            throw e;
        }

        return CancelOrderResponse.from(order);
    }

    //주문ID로 주문삭제, 취소된(CANCELLED) 주문만 가능
    public void deleteOrderById(UUID orderId) {
        Order order = orderTransactionService.delete(orderId, AuthContext.getUserId());

        try {
            clientService.deleteDelivery(order.getDeliveryId());
        } catch (RuntimeException e) {
            //배송삭제 실패시 주문/주문상품 삭제를 되돌리는 보상시나리오
            orderCompensator.compensateDelete(orderId);
            throw e;
        }
    }

    private OrderItem prepareCreateOrder(CreateOrderRequest request, FetchUserByIdResponse user) {
        orderValidator.validateNotSameCompany(request.getRequesterId(), request.getSupplierId());
        orderValidator.validateUserBelongsToRequester(user.getCompanyId(), request.getRequesterId());
        FetchItemByIdResponse item = clientService.fetchItemById(request.getItemId());
        orderValidator.validateOrderItem(item, request);

        //CompanyService API요청 회사정보 조회
        FetchCompanyByIdResponse requester = clientService.fetchCompanyById(request.getRequesterId());
        FetchCompanyByIdResponse supplier = clientService.fetchCompanyById(request.getSupplierId());

        //주문 생성
        Order order = Order.builder()
                .orderUserId(AuthContext.getUserId())
                .requesterId(request.getRequesterId())
                .destinationHubId(requester.getHubId())
                .address(requester.getAddress())
                .requestNote(request.getRequestNote())
                .build();

        //주문상품 생성
        OrderItem orderItem = OrderItem.builder()
                .itemId(item.getItemId())
                .itemName(item.getName())
                .price(item.getPrice())
                .quantity(request.getQuantity())
                .departureHubId(supplier.getHubId())
                .supplierId(supplier.getCompanyId())
                .order(order)
                .build();

        orderTransactionService.save(order, orderItem);

        return orderItem;
    }
}

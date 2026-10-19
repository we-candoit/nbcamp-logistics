package logistics.order.application.service;

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

    //주문생성
    public CreateOrderResponse create(CreateOrderRequest request) {
        //올바른 요청 및 상품 인지 검증
        orderValidator.validateOrder(request);
        FetchItemByIdResponse item = clientService.fetchItemById(request.getItemId());
        orderValidator.validateOrderItem(item, request);

        //CompanyService API요청 회사정보 조회
        FetchCompanyByIdResponse requesterCompany = clientService.fetchCompanyById(request.getRequesterId());
        FetchCompanyByIdResponse supplierCompany = clientService.fetchCompanyById(request.getSupplierId());

        //주문엔티티 생성 / WAITING 상태
        Order order = buildOrder(request, requesterCompany);
        OrderItem orderItem = buildOrderItem(request, supplierCompany, item, order);

        //재고감소
        clientService.decreaseStock(supplierCompany.getHubId(), item.getItemId(), request.getQuantity());

        //배송생성
        CreateDeliveryResponse createDeliveryResponse = clientService.createDelivery(
                CreateDeliveryRequest.of(order, orderItem, request.getRecipientName(), request.getRecipientPhone())
        );

        //주문저장
        order.confirm(createDeliveryResponse.getDeliveryId());
        orderTransactionService.save(order, orderItem);

        //Slack알림

        return new CreateOrderResponse(
                order.getId(),
                orderItem.getItemId(),
                order.getDeliveryId(),
                order.getCreatedAt()
        );
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
            orderTransactionService.compensateCancelOrder(orderId);
            throw e;
        }

        return CancelOrderResponse.from(order);
    }

    private OrderItem buildOrderItem(CreateOrderRequest request, FetchCompanyByIdResponse supplierCompany,
                                      FetchItemByIdResponse item, Order order) {
        return new OrderItem(
            item.getItemId(),
            item.getName(),
            item.getPrice(),
            request.getQuantity(),
            supplierCompany.getHubId(),
            supplierCompany.getCompanyId(),
            order
        );
    }
    private Order buildOrder(CreateOrderRequest request, FetchCompanyByIdResponse requesterCompany) {
        return new Order(
                AuthContext.getUserId(),
                request.getRequesterId(),
                requesterCompany.getHubId(),
                requesterCompany.getAddress(),
                request.getRequestNote()
        );
    }

}

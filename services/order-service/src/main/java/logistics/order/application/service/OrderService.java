package logistics.order.application.service;

import logistics.order.application.validator.OrderValidator;
import logistics.order.common.AuthContext;
import logistics.order.domain.entity.Order;
import logistics.order.domain.entity.OrderItem;
import logistics.order.infrastructure.client.CompanyServiceClient;
import logistics.order.infrastructure.client.DeliveryServiceClient;
import logistics.order.infrastructure.client.HubServiceClient;
import logistics.order.infrastructure.client.dto.*;
import logistics.order.presentation.dto.request.CreateOrderRequest;
import logistics.order.presentation.dto.response.CreateOrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final CompanyServiceClient companyServiceClient;
    private final HubServiceClient hubServiceClient;
    private final DeliveryServiceClient deliveryServiceClient;
    private final OrderValidator orderValidator;
    private final OrderTransactionService orderTransactionService;

    public CreateOrderResponse create(CreateOrderRequest request) {
        //올바른 요청 및 상품 인지 검증
        orderValidator.validateOrder(request);
        FetchItemByIdResponse item = companyServiceClient.fetchItemById(request.getItemId());
        orderValidator.validateOrderItem(item, request);

        //CompanyService API요청 회사정보 조회
        FetchCompanyByIdResponse requesterCompany = companyServiceClient.fetchCompanyById(request.getRequesterId());
        FetchCompanyByIdResponse supplierCompany = companyServiceClient.fetchCompanyById(request.getSupplierId());

        //주문엔티티 생성 / WAITING 상태
        Order order = buildOrder(request, requesterCompany);
        OrderItem orderItem = buildOrderItem(request, supplierCompany, item, order);

        //재고감소
        hubServiceClient.decreaseStock(supplierCompany.getHubId(), item.getItemId(), request.getQuantity());

        //배송생성
        CreateDeliveryResponse createDeliveryResponse = deliveryServiceClient.createDelivery(
                buildCreateDeliveryRequest(order, orderItem, request)
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

    //배송생성 API Request 빌드
    private CreateDeliveryRequest buildCreateDeliveryRequest(Order order, OrderItem orderItems,
                                                             CreateOrderRequest createOrderDto) {
        return new CreateDeliveryRequest(
                order.getId(),
                orderItems.getDepartureHubId(),
                order.getDestinationHubId(),
                order.getAddress(),
                createOrderDto.getRecipientName(),
                createOrderDto.getRecipientPhone()
        );
    }

}

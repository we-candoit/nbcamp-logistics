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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderValidator orderValidator;
    private final OrderTransactionService orderTransactionService;
    private final ClientService clientService;

    //주문생성
    public CreateOrderResponse create(CreateOrderRequest request) {
        orderValidator.validateNotSameCompany(request.getRequesterId(), request.getSupplierId());
        FetchUserByIdResponse user = clientService.fetchUserById(AuthContext.getUserId());
        orderValidator.validateUserBelongsToRequester(user.getCompanyId(), request.getRequesterId());
        FetchItemByIdResponse item = clientService.fetchItemById(request.getItemId());
        orderValidator.validateOrderItem(item, request);

        //CompanyService API요청 회사정보 조회
        FetchCompanyByIdResponse requester = clientService.fetchCompanyById(request.getRequesterId());
        FetchCompanyByIdResponse supplier = clientService.fetchCompanyById(request.getSupplierId());

        //주문생성 / WAITING 상태
        Order order = buildOrder(AuthContext.getUserId(), request, requester);
        OrderItem orderItem = buildOrderItem(request, supplier, item, order);
        orderTransactionService.save(order, orderItem);

        //보상 범위 판단용 / 성공한 단계만 되돌림
        boolean stockDecreased = false;
        UUID deliveryId = null;

        try {
            //재고감소
            clientService.decreaseStock(supplier.getHubId(), item.getItemId(), request.getQuantity());
            stockDecreased = true;

            //배송생성
            CreateDeliveryResponse delivery = clientService.createDelivery(
                    CreateDeliveryRequest.of(order, orderItem, request.getRecipientName(), request.getRecipientPhone())
            );
            deliveryId = delivery.getDeliveryId();

            //주문확정 / CONFIRMED 상태
            order.confirm(deliveryId);
            orderTransactionService.save(order, orderItem);
        } catch (RuntimeException e) {
            //주문생성 보상시나리오 / 성공한 단계를 역순으로 되돌린 뒤 결과에 따라 주문상태 변경
            boolean compensated = compensateCreateOrder(supplier.getHubId(), item.getItemId(),
                    request.getQuantity(), stockDecreased, deliveryId);
            failCreateOrder(order.getId(), compensated);
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
            orderTransactionService.compensateCancelOrder(orderId);
            throw e;
        }

        return CancelOrderResponse.from(order);
    }

    //주문ID로 주문삭제 / 취소된(CANCELLED) 주문만 가능, 배송삭제에 실패하면 주문삭제 복구
    public void deleteOrderById(UUID orderId) {
        Order order = orderTransactionService.delete(orderId, AuthContext.getUserId());

        try {
            clientService.deleteDelivery(order.getDeliveryId());
        } catch (RuntimeException e) {
            //배송삭제 실패시 주문/주문상품 삭제를 되돌리는 보상시나리오
            orderTransactionService.compensateDeleteOrder(orderId);
            throw e;
        }
    }

    //주문생성 보상 / 하나가 실패해도 나머지 보상은 계속 수행, 모두 성공하면 true
    private boolean compensateCreateOrder(UUID hubId, UUID itemId, Long quantity,
                                          boolean stockDecreased, UUID deliveryId) {
        boolean compensated = true;

        if (deliveryId != null) {
            //배송은 취소된 상태에서만 삭제 가능
            compensated &= retryOnce("배송취소", () -> clientService.cancelDelivery(deliveryId))
                    && retryOnce("배송삭제", () -> clientService.deleteDelivery(deliveryId));
        }
        if (stockDecreased) {
            compensated &= retryOnce("재고복구", () -> clientService.restoreStock(hubId, itemId, quantity));
        }
        return compensated;
    }

    //보상 실패시 1회 재시도
    private boolean retryOnce(String action, Runnable compensation) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                compensation.run();
                return true;
            } catch (RuntimeException e) {
                log.warn("주문생성 보상 실패 action={} attempt={}", action, attempt, e);
            }
        }
        log.error("주문생성 보상 최종 실패 action={}", action);
        return false;
    }

    //보상 결과에 따라 주문상태 변경 / 여기서 예외가 나면 원래 실패 원인이 가려지므로 로그만 남김
    private void failCreateOrder(UUID orderId, boolean compensated) {
        try {
            orderTransactionService.failCreate(orderId, compensated);
        } catch (RuntimeException e) {
            log.error("주문생성 실패 상태변경 실패 orderId={} compensated={}", orderId, compensated, e);
        }
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
    private Order buildOrder(UUID orderUserId, CreateOrderRequest request, FetchCompanyByIdResponse requesterCompany) {
        return new Order(
                orderUserId,
                request.getRequesterId(),
                requesterCompany.getHubId(),
                requesterCompany.getAddress(),
                request.getRequestNote()
        );
    }

}

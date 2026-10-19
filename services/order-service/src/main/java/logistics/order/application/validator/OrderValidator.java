package logistics.order.application.validator;

import logistics.order.common.AuthContext;
import logistics.order.domain.entity.Order;
import logistics.order.domain.enums.OrderStatus;
import logistics.order.infrastructure.client.UserServiceClient;
import logistics.order.infrastructure.client.dto.CancelDeliveryResponse;
import logistics.order.infrastructure.client.dto.FetchItemByIdResponse;
import logistics.order.infrastructure.client.dto.FetchUserByIdResponse;
import logistics.order.infrastructure.client.enums.DeliveryStatus;
import logistics.order.presentation.dto.request.CreateOrderRequest;
import logistics.order.presentation.exception.CustomException;
import logistics.order.presentation.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

import static logistics.order.application.validator.CommonValidator.validateEqual;
import static logistics.order.application.validator.CommonValidator.validateNotEqual;

@Component
@RequiredArgsConstructor
public class OrderValidator {
    
    private final UserServiceClient userServiceClient;

    //유효한 주문인가?
    public void validateOrder(CreateOrderRequest request) {
        //요청업체와 공급업체가 다른가?
        validateNotEqual(request.getRequesterId(), request.getSupplierId(), ErrorCode.C400_SAME_COMPANY);
        validateUser(request.getRequesterId());
    }

    //유효한 주문상품인가?
    public void validateOrderItem(FetchItemByIdResponse item, CreateOrderRequest request) {
        //공급업체에 해당 상품이 실제로 있는가?
        validateEqual(item.getCompanyId(), request.getSupplierId(), ErrorCode.C400_BAD_REQUEST);

        //가격이 일치하는가?
        validateEqual(item.getPrice(), request.getPrice(), ErrorCode.C409_ORDER_PRICE_CHANGED);
    }

    //주문한 유저가 해당 요청업체의 소속인가?
    private void validateUser(UUID requesterId) {
        if (!AuthContext.isMaster()) {
            FetchUserByIdResponse user = userServiceClient.fetchUserById(AuthContext.getUserId());

            validateEqual(user.getCompanyId(), requesterId, ErrorCode.C403_FORBIDDEN_REQUESTER);
        }
    }

    //MASTER는 모든 주문 가능 / 그 외는 본인이 생성한 주문만 가능
    public void validateOrderOwner(UUID orderUserId) {
        if (!AuthContext.isMaster()) {
            validateEqual(orderUserId, AuthContext.getUserId(), ErrorCode.C403_FORBIDDEN_ORDER);
        }
    }

    //조회할 주문상태가 유효한가? / 없으면 null(전체), 유효하지 않으면 예외 (대소문자 구분)
    public void validateOrderStatus(String status) {
        try {
            if (status == null) {
                return ;
            }
            OrderStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.C400_INVALID_ORDER_STATUS);
        }
    }

    //취소 가능한 주문인가? / 배송 전(CONFIRMED) 상태에서만 가능
    public void validateCancelable(Order order) {
        if (!order.isCancelable()) {
            throw new CustomException(ErrorCode.C409_INVALID_ORDER_STATUS);
        }
    }

    //배송이 취소되었는가? / 배송 서비스의 취소 상태값은 CANCELLED
    public void validateDeliveryCanceled(CancelDeliveryResponse response) {
        if (response == null ||
                !DeliveryStatus.CANCELLED.name().equals(response.getStatus())) {
            throw new CustomException(ErrorCode.C409_ORDER_NOT_CANCELABLE);
        }
    }
}

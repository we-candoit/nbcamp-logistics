package logistics.order.application.validator;

import logistics.order.common.AuthContext;
import logistics.order.infrastructure.client.UserServiceClient;
import logistics.order.infrastructure.client.dto.FetchItemByIdResponse;
import logistics.order.infrastructure.client.dto.FetchUserByIdResponse;
import logistics.order.presentation.dto.request.CreateOrderRequest;
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
        if (!AuthContext.getUserRole().equals("MASTER")) {
            FetchUserByIdResponse user = userServiceClient.fetchUserById(AuthContext.getUserId());

            validateEqual(user.getCompanyId(), requesterId, ErrorCode.C403_FORBIDDEN_REQUESTER);
        }
    }
}

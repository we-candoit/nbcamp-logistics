package logistics.order.application.validator;

import logistics.order.common.AuthContext;
import logistics.order.infrastructure.client.dto.CancelDeliveryResponse;
import logistics.order.infrastructure.client.dto.FetchItemByIdResponse;
import logistics.order.infrastructure.client.enums.DeliveryCancelResult;
import logistics.order.presentation.dto.request.CreateOrderRequest;
import logistics.order.common.exception.CustomException;
import logistics.order.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.*;

import static logistics.order.application.validator.CommonValidator.validateEqual;
import static logistics.order.application.validator.CommonValidator.validateNotEqual;

//외부 서비스 조회는 하지 않고, 전달받은 값으로 검증만 수행 (조회는 OrderService → ClientService)
@Component
public class OrderValidator {

    //요청업체와 공급업체가 다른가?
    public void validateNotSameCompany(UUID requesterId, UUID supplierId) {
        validateNotEqual(requesterId, supplierId, ErrorCode.C400_SAME_COMPANY);
    }

    //유효한 주문상품인가?
    public void validateOrderItem(FetchItemByIdResponse item, CreateOrderRequest request) {
        //공급업체에 해당 상품이 실제로 있는가?
        validateEqual(item.getCompanyId(), request.getSupplierId(), ErrorCode.C400_BAD_REQUEST);

        //가격이 일치하는가?
        validateEqual(item.getPrice(), request.getPrice(), ErrorCode.C409_ORDER_PRICE_CHANGED);
    }

    //주문한 유저가 해당 요청업체의 소속인가?
    public void validateUserBelongsToRequester(UUID userCompanyId, UUID requesterId) {
        if (!AuthContext.isMaster()) {
            validateEqual(userCompanyId, requesterId, ErrorCode.C403_FORBIDDEN_REQUESTER);
        }
    }

    //MASTER는 모든 주문 가능 / 그 외는 본인이 생성한 주문만 가능
    public void validateOrderOwner(UUID orderUserId) {
        if (!AuthContext.isMaster()) {
            validateEqual(orderUserId, AuthContext.getUserId(), ErrorCode.C403_FORBIDDEN_ORDER);
        }
    }

    //배송이 취소되었는가? / 배송이 취소됐거나(CANCELLED) 배송이 없어 생성이 차단됐으면(CREATION_BLOCKED) 진행 중인 배송 없음
    public void validateDeliveryCanceled(CancelDeliveryResponse response) {
        if (response == null || !isDeliveryCanceled(response.getResult())) {
            throw new CustomException(ErrorCode.C409_ORDER_NOT_CANCELABLE);
        }
    }

    private boolean isDeliveryCanceled(String result) {
        return DeliveryCancelResult.CANCELLED.name().equals(result)
                || DeliveryCancelResult.CREATION_BLOCKED.name().equals(result);
    }
}

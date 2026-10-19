package logistics.order.application.service;

import feign.FeignException;
import logistics.order.application.validator.OrderValidator;
import logistics.order.common.exception.CustomException;
import logistics.order.common.exception.ErrorCode;
import logistics.order.infrastructure.client.CompanyServiceClient;
import logistics.order.infrastructure.client.DeliveryServiceClient;
import logistics.order.infrastructure.client.HubServiceClient;
import logistics.order.infrastructure.client.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

//외부 서비스 호출 및 Feign 예외 변환 / 보상처리 등 흐름 제어는 OrderService에서 담당
@Service
@RequiredArgsConstructor
public class ClientService {

    private final CompanyServiceClient companyServiceClient;
    private final HubServiceClient hubServiceClient;
    private final DeliveryServiceClient deliveryServiceClient;
    private final OrderValidator orderValidator;

    //상품 조회
    public FetchItemByIdResponse fetchItemById(UUID itemId) {
        return companyServiceClient.fetchItemById(itemId);
    }

    //업체 조회
    public FetchCompanyByIdResponse fetchCompanyById(UUID companyId) {
        return companyServiceClient.fetchCompanyById(companyId);
    }

    //재고 차감
    public void decreaseStock(UUID hubId, UUID itemId, Long quantity) {
        hubServiceClient.decreaseStock(hubId, itemId, quantity);
    }

    //배송 생성
    public CreateDeliveryResponse createDelivery(CreateDeliveryRequest request) {
        return deliveryServiceClient.createDelivery(request);
    }

    //배송취소 요청 / 배송이 취소되지 않으면 주문도 취소 불가
    public void cancelDelivery(UUID deliveryId) {
        try {
            CancelDeliveryResponse response = deliveryServiceClient.cancelDelivery(deliveryId);
            orderValidator.validateDeliveryCanceled(response);
        } catch (FeignException e) {
            //배송 서비스가 4xx로 응답 (409 이미 출발한 배송, 404 배송 없음 등) → 취소 불가
            if (e.status() >= 400 && e.status() < 500) {
                throw new CustomException(ErrorCode.C409_ORDER_NOT_CANCELABLE);
            }
            //응답 없음(-1), 5xx는 전역 처리기로 전달
            throw e;
        }
    }
}

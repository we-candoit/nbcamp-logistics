package logistics.order.application.service;

import feign.FeignException;
import logistics.order.application.validator.OrderValidator;
import logistics.order.common.exception.CustomException;
import logistics.order.common.exception.ErrorCode;
import logistics.order.infrastructure.client.*;
import logistics.order.infrastructure.client.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

//외부 서비스 호출 및 Feign 예외 변환 / 보상처리 등 흐름 제어는 OrderService에서 담당
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientService {

    private final CompanyServiceClient companyServiceClient;
    private final HubServiceClient hubServiceClient;
    private final DeliveryServiceClient deliveryServiceClient;
    private final UserServiceClient userServiceClient;
    private final SlackClient slackClient;
    private final OrderValidator orderValidator;

    //유저 조회 / 요청한 유저가 없으면 요청업체 소속을 확인할 수 없어 권한 없음
    //유저 서비스는 응답을 data로 감싸서 반환, data가 없으면 유저 정보를 확인할 수 없어 서버 오류
    public FetchUserByIdResponse fetchUserById(UUID userId) {
        UserApiResponse<FetchUserByIdResponse> response;
        try {
            response = userServiceClient.fetchUserById(userId);
        } catch (FeignException.NotFound e) {
            throw new CustomException(ErrorCode.C404_USER_NOT_FOUND);
        }

        if (response == null || response.getData() == null) {
            throw new CustomException(ErrorCode.C500_INTERNAL_SERVER_ERROR);
        }
        return response.getData();
    }

    //상품 조회 / 요청한 상품이 없으면 잘못된 요청
    public FetchItemByIdResponse fetchItemById(UUID itemId) {
        try {
            return companyServiceClient.fetchItemById(itemId);
        } catch (FeignException.NotFound e) {
            throw new CustomException(ErrorCode.C404_ITEM_NOT_FOUND);
        }
    }

    //업체 조회 / 요청한 업체가 없으면 잘못된 요청
    public FetchCompanyByIdResponse fetchCompanyById(UUID companyId) {
        try {
            return companyServiceClient.fetchCompanyById(companyId);
        } catch (FeignException.NotFound e) {
            throw new CustomException(ErrorCode.C404_COMPANY_NOT_FOUND);
        }
    }

    //재고 차감
    public void decreaseStock(UUID hubId, UUID itemId, Long quantity) {
        hubServiceClient.decreaseStock(hubId, itemId, quantity);
    }

    //재고 복구
    public void restoreStock(UUID hubId, UUID itemId, Long quantity) {
        hubServiceClient.restoreStock(hubId, itemId, quantity);
    }

    //배송 생성
    public CreateDeliveryResponse createDelivery(CreateDeliveryRequest request) {
        return deliveryServiceClient.createDelivery(request);
    }

    //주문ID로 배송취소 요청 / 배송이 취소되지 않으면 주문도 취소 불가
    public void cancelDeliveryByOrderId(UUID orderId) {
        try {
            CancelDeliveryResponse response = deliveryServiceClient.cancelDeliveryByOrderId(orderId);
            orderValidator.validateDeliveryCanceled(response);
        } catch (FeignException e) {
            //배송 서비스가 4xx로 응답 (409 이미 출발한 배송, 404 배송 없음 등) → 취소 불가
            if (e.status() == 404) {
                throw new CustomException(ErrorCode.C404_DELIVERY_NOT_FOUND);
            } else if (e.status() >= 400 && e.status() < 500) {
                throw new CustomException(ErrorCode.C409_ORDER_NOT_CANCELABLE);
            }

            //응답 없음(-1), 5xx는 전역 처리기로 전달
            throw e;
        }
    }

    //주문ID로 배송삭제 요청 / 배송이 삭제되지 않으면 주문도 삭제 불가
    public void deleteDeliveryByOrderId(UUID orderId) {
        try {
            deliveryServiceClient.deleteDeliveryByOrderId(orderId);
        } catch (FeignException e) {
            //배송 서비스가 4xx로 응답 (409 취소되지 않은 배송, 404 배송 없음 등) → 삭제 불가
            if (e.status() == 404) {
                throw new CustomException(ErrorCode.C404_DELIVERY_NOT_FOUND);
            } else if (e.status() >= 400 && e.status() < 500) {
                throw new CustomException(ErrorCode.C409_ORDER_NOT_DELETABLE);
            }

            //응답 없음(-1), 5xx는 전역 처리기로 전달
            throw e;
        }
    }

    //슬랙 DM 전송 / 알림 실패가 주문 처리 결과에 영향을 주지 않도록 예외는 로그만 남김
    public void sendSlackDm(String slackUserId, String message) {
        try {
            slackClient.sendDm(slackUserId, message);
        } catch (RuntimeException e) {
            log.warn("슬랙 DM 전송 실패 slackUserId={}", slackUserId, e);
        }
    }
}

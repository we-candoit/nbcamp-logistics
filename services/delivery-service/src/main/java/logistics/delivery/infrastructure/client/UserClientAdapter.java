package logistics.delivery.infrastructure.client;

import feign.FeignException;
import logistics.delivery.common.exception.DeliveryErrorCode;
import logistics.delivery.common.exception.DeliveryException;
import logistics.delivery.infrastructure.client.dto.UserApiResponse;
import logistics.delivery.infrastructure.client.dto.UserDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserClientAdapter {
    private final UserClient userClient;

    /**
     * 배정 후보 사용자를 조회하고 응답의 사용자 ID와 필수 필드를 검증한다.
     * 배송 담당 자격 및 허브 일치 정책은 애플리케이션 서비스에서 판단한다.
     *
     * @param managerId 배정할 사용자의 ID
     * @param requesterId 원 요청자의 사용자 ID
     * @param role 원 요청자의 역할
     * @return 필수 필드가 확인된 사용자 응답
     * @throws DeliveryException 사용자 없음, 호출 실패 또는 잘못된 응답인 경우
     */
    public UserDetailResponse getAssignmentCandidate(UUID managerId, UUID requesterId, String role) {
        UserApiResponse<UserDetailResponse> response;
        try {
            response = userClient.getUserDetail(managerId, requesterId, role);
        } catch (FeignException exception) {
            if (exception.status() == 404) {
                throw new DeliveryException(DeliveryErrorCode.ASSIGNEE_NOT_FOUND, exception);
            }
            throw new DeliveryException(DeliveryErrorCode.USER_SERVICE_UNAVAILABLE, exception);
        }

        if (response == null || !Boolean.TRUE.equals(response.success())) {
            throw new DeliveryException(DeliveryErrorCode.INVALID_USER_SERVICE_RESPONSE);
        }

        UserDetailResponse user = response.data();
        if (user == null || !managerId.equals(user.userId()) || user.isDeliveryManager() == null
                || user.affiliationType() == null || user.affiliationStatus() == null) {
            throw new DeliveryException(DeliveryErrorCode.INVALID_USER_SERVICE_RESPONSE);
        }
        return user;
    }
}

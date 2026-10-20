package logistics.delivery.infrastructure.client;

import feign.FeignException;
import logistics.delivery.common.exception.DeliveryErrorCode;
import logistics.delivery.common.exception.DeliveryException;
import logistics.delivery.infrastructure.client.dto.HubRouteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class HubClientAdapter {
    private final HubClient hubClient;

    /**
     * 허브 이동 정보를 조회하고 요청한 허브 및 거리·시간 응답을 검증한다.
     *
     * @param departureHubId 출발 허브 ID
     * @param destinationHubId 도착 허브 ID
     * @return 검증된 허브 이동 정보
     * @throws DeliveryException 호출 실패 또는 응답이 올바르지 않은 경우
     */
    public HubRouteResponse getRoute(UUID departureHubId, UUID destinationHubId) {
        HubRouteResponse route;
        try {
            route = hubClient.getRoute(departureHubId, destinationHubId);
        } catch (FeignException exception) {
            throw new DeliveryException(DeliveryErrorCode.HUB_SERVICE_UNAVAILABLE, exception);
        }

        if (route == null || route.distance() == null || route.duration() == null
                || !departureHubId.equals(route.originHubId())
                || !destinationHubId.equals(route.destinationHubId())
                || route.distance().signum() < 0 || route.duration() < 0) {
            throw new DeliveryException(DeliveryErrorCode.INVALID_HUB_SERVICE_RESPONSE);
        }
        return route;
    }
}

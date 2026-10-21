package logistics.delivery.application;

import jakarta.annotation.PostConstruct;
import logistics.delivery.config.DeliveryDemoProperties;
import logistics.delivery.domain.entity.Delivery;
import logistics.delivery.domain.entity.DeliveryRoute;
import logistics.delivery.domain.enums.DeliveryRouteStatus;
import logistics.delivery.domain.enums.RouteType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** 실측이나 배정 알고리즘 대신 설정값을 사용하는 시연 전용 처리. 호출자의 트랜잭션에 참여한다. */
@Slf4j
@Service
@Profile("demo")
@RequiredArgsConstructor
public class DeliveryDemoService {

    private final DeliveryDemoProperties properties;

    /** 시연 기능의 활성화 상태를 시작 로그에 표시한다. */
    @PostConstruct
    public void logDemoConfiguration() {
        log.info("배송 demo 프로필 활성화: 자동 배정={}, 고정 거리·시간 기록={}",
                properties.getAutoAssignment().isEnabled(), properties.getActualMetrics().isEnabled());
    }

    /**
     * 신규 배송 경로에 출발 허브별 고정 담당자를 배정한다.
     * 매핑이 없는 허브는 미배정으로 두고, 업체 배송 담당자는 배송 전체에도 반영한다.
     * 사용자 API를 호출하지 않으므로 설정 계정의 자격과 소속은 사전에 확인해야 한다.
     *
     * @param delivery 신규 배송
     * @param routes 신규 배송 경로 목록
     */
    public void assignManagers(Delivery delivery, List<DeliveryRoute> routes) {
        if (!properties.getAutoAssignment().isEnabled()) {
            return;
        }

        for (DeliveryRoute route : routes) {
            if (route.getManagerId() != null) {
                continue;
            }

            UUID managerId = properties.getAutoAssignment().getHubManagers()
                    .get(route.getDepartureHubId());
            if (managerId == null) {
                continue;
            }

            route.assignManager(managerId);
            if (route.getType() == RouteType.HUB_TO_COMPANY) {
                delivery.assignManager(managerId);
            }
        }
    }

    /**
     * 처음 도착 처리되는 배송 경로에 실측이 아닌 시연용 거리와 시간을 기록한다.
     * 이미 저장된 실적값은 유지한다.
     *
     * @param route 도착 상태로 변경된 배송 경로
     */
    public void recordActualMetrics(DeliveryRoute route) {
        if (!properties.getActualMetrics().isEnabled()
                || route.getStatus() != DeliveryRouteStatus.ARRIVED) {
            return;
        }

        DeliveryDemoProperties.Metrics metrics = route.getType() == RouteType.HUB_TO_HUB
                ? properties.getActualMetrics().getHubToHub()
                : properties.getActualMetrics().getHubToCompany();
        route.recordActualMetricsIfAbsent(metrics.getDistance(), metrics.getDuration());
    }
}

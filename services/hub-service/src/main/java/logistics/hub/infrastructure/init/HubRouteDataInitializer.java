package logistics.hub.infrastructure.init;

import logistics.hub.application.service.HaversineCalculator;
import logistics.hub.common.TemporaryUserIds;
import logistics.hub.domain.entity.Hub;
import logistics.hub.domain.entity.HubRoute;
import logistics.hub.infrastructure.repository.HubRepository;
import logistics.hub.infrastructure.repository.HubRouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.seed.haversine-routes",
        havingValue = "true"
)
public class HubRouteDataInitializer implements CommandLineRunner {

    private final HubRepository hubRepository;
    private final HubRouteRepository hubRouteRepository;

    @Override
    @Transactional
    public void run(String... args) {

        List<Hub> hubs =
                hubRepository.findAllByDeletedAtIsNullOrderByNameAsc();

        int insertedCount = 0;

        for (Hub origin : hubs) {
            for (Hub destination : hubs) {

                // 자기 자신으로 향하는 경로 제외
                if (origin.getId().equals(destination.getId())) {
                    continue;
                }

                // 기존 경로 확인(중복 방지)
                if (hubRouteRepository
                        .existsByOriginHub_IdAndDestinationHub_Id(
                                origin.getId(),
                                destination.getId())) {
                    continue;
                }

                BigDecimal distance =
                        HaversineCalculator.calculateDistanceKm(
                                origin.getLatitude(),
                                origin.getLongitude(),
                                destination.getLatitude(),
                                destination.getLongitude()
                        );

                int duration =
                        HaversineCalculator.estimateDurationMinutes(distance);

                hubRouteRepository.save(
                        new HubRoute(
                                origin,
                                destination,
                                distance,
                                duration,
                                TemporaryUserIds.LOCAL_MASTER_ID
                        )
                );

                insertedCount++;
            }
        }

        log.info("허브 직선 경로: {}개 추가 (총 {}개 예상)",
                insertedCount, hubs.size() * (hubs.size() - 1));

    }
}
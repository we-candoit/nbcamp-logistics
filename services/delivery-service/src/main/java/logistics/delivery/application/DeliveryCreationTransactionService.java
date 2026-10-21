package logistics.delivery.application;

import logistics.delivery.common.exception.DeliveryErrorCode;
import logistics.delivery.common.exception.DeliveryException;
import logistics.delivery.domain.entity.Delivery;
import logistics.delivery.domain.entity.DeliveryOrderControl;
import logistics.delivery.domain.entity.DeliveryRoute;
import logistics.delivery.domain.enums.RouteType;
import logistics.delivery.infrastructure.client.dto.HubRouteResponse;
import logistics.delivery.infrastructure.repository.DeliveryRepository;
import logistics.delivery.infrastructure.repository.DeliveryRouteRepository;
import logistics.delivery.presentation.dto.request.DeliveryCreateRequest;
import logistics.delivery.presentation.dto.response.DeliveryCreateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryCreationTransactionService {

    private final DeliveryOrderControlManager controlManager;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryRouteRepository deliveryRouteRepository;

    /**
     * 관리 기록을 잠그고 배송 생성 가능 여부와 기존 배송을 확인
     * 관리 기록의 초기화가 완료된 후 호출
     *
     * @param request 배송 생성 요청
     * @return 동일 요청으로 생성된 기존 배송, 없다면 빈 Optional
     */
    @Transactional
    public Optional<DeliveryCreateResult> findExisting(DeliveryCreateRequest request) {
        DeliveryOrderControl control = controlManager.getForUpdate(request.orderId());

        Delivery existingDelivery = deliveryRepository.findByOrderId(request.orderId())
                .orElse(null);

        if (existingDelivery != null && existingDelivery.getDeletedAt() != null) {
            throw new DeliveryException(DeliveryErrorCode.DELETED_DELIVERY_RECREATION_NOT_ALLOWED
            );
        }

        if (control.isCreationBlocked()) {
            throw new DeliveryException(DeliveryErrorCode.DELIVERY_CREATION_BLOCKED);
        }

        if (existingDelivery == null) {
            return Optional.empty();
        }

        boolean sameRequest = existingDelivery.matchesCreation(
                request.departureHubId(),
                request.destinationHubId(),
                request.address(),
                request.recipientName(),
                request.recipientPhone()
        );

        if (!sameRequest) {
            throw new DeliveryException(DeliveryErrorCode.DELIVERY_CREATION_CONFLICT);
        }

        return Optional.of(
                new DeliveryCreateResult(DeliveryCreateResponse.from(existingDelivery), false)
        );
    }

    /**
     * 관리 기록을 잠그고 생성 가능 여부를 다시 확인한 뒤 배송 저장
     * 허브 API 조회 후에 이 메서드를 호출
     *
     * @param request 배송 생성 요청
     * @param hubRoute 다른 허브 사이의 이동 정보, 같은 허브라면 null
     * @return 신규 생성 또는 기존 배송 반환 결과
     */
    @Transactional
    public DeliveryCreateResult create(DeliveryCreateRequest request, HubRouteResponse hubRoute) {
        Optional<DeliveryCreateResult> existing = findExisting(request);

        if (existing.isPresent()) return existing.get();

        Delivery delivery = request.toEntity();
        List<DeliveryRoute> routes = createRoutes(delivery, hubRoute);

        deliveryRepository.save(delivery);
        deliveryRouteRepository.saveAll(routes);

        return new DeliveryCreateResult(DeliveryCreateResponse.from(delivery),
                true);
    }

    /**
     * 미리 조회한 허브 이동 정보로 배송 경로를 구성
     * 외부 API를 호출X
     *
     * @param delivery 생성할 배송
     * @param hubRoute 다른 허브 사이의 이동 정보, 같은 허브라면 null
     * @return 순서대로 구성된 배송 경로 목록
     */
    private List<DeliveryRoute> createRoutes(Delivery delivery, HubRouteResponse hubRoute) {
        List<DeliveryRoute> routes = new ArrayList<>();

        UUID departureHubId = delivery.getDepartureHubId();
        UUID destinationHubId = delivery.getDestinationHubId();

        if (!departureHubId.equals(destinationHubId)) {
            if (hubRoute == null) {
                throw new DeliveryException(DeliveryErrorCode.INVALID_HUB_SERVICE_RESPONSE);
            }

            DeliveryRoute hubToHub = DeliveryRoute.builder()
                    .delivery(delivery)
                    .sequence(1)
                    .departureHubId(departureHubId)
                    .destinationHubId(destinationHubId)
                    .expectedDistance(hubRoute.distance())
                    .expectedDuration(hubRoute.duration())
                    .type(RouteType.HUB_TO_HUB)
                    .build();

            routes.add(hubToHub);
        }

        DeliveryRoute hubToCompany = DeliveryRoute.builder()
                .delivery(delivery)
                .sequence(routes.size() + 1)
                .departureHubId(destinationHubId)
                .type(RouteType.HUB_TO_COMPANY)
                .build();

        routes.add(hubToCompany);

        return routes;
    }
}

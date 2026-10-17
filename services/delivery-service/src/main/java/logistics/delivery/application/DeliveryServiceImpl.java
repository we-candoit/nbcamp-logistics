package logistics.delivery.application;

import logistics.delivery.domain.entity.Delivery;
import logistics.delivery.domain.entity.DeliveryRoute;
import logistics.delivery.domain.enums.DeliveryStatus;
import logistics.delivery.domain.enums.RouteType;
import logistics.delivery.infrastructure.client.HubClient;
import logistics.delivery.infrastructure.client.dto.HubRouteResponse;
import logistics.delivery.infrastructure.repository.DeliveryRepository;
import logistics.delivery.infrastructure.repository.DeliveryRouteRepository;
import logistics.delivery.presentation.dto.request.DeliveryCreateRequest;
import logistics.delivery.presentation.dto.response.DeliveryCancelResponse;
import logistics.delivery.presentation.dto.response.DeliveryCreateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DeliveryServiceImpl implements DeliveryService{

    private final DeliveryRepository deliveryRepository;
    private final DeliveryRouteRepository deliveryRouteRepository;
    private final HubClient hubClient;
    private final AuditorAware<String> auditorAware;

    /**
     * 배송 생성
     * 주문 서비스에게 배송 생성을 요청 받아 배송과 배송 경로를 생성하는 메서드
     * @param request 배송 생성 요청
     * @return 배송 생성 응답
     */
    @Override
    @Transactional
    public DeliveryCreateResult createDelivery(DeliveryCreateRequest request) {

        // 기존에 해당 orderId로 생성된 배송이 있는지 확인
        Delivery existingDelivery = deliveryRepository
                .findByOrderId(request.orderId())
                .orElse(null);

        if (existingDelivery != null) {
            if (existingDelivery.getDeletedAt() != null) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "이미 삭제된 배송은 다시 생성할 수 없습니다."
                );
            }

            boolean sameRequest = existingDelivery.matchesCreation(
                    request.departureHubId(),
                    request.destinationHubId(),
                    request.address(),
                    request.recipientName(),
                    request.recipientPhone()
            );

            if (!sameRequest) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "같은 주문에 다른 내용의 배송이 이미 존재합니다.");
            }

            return new DeliveryCreateResult(
                    DeliveryCreateResponse.from(existingDelivery),
                    false
            );
        }

        // 새로운 배송 생성
        Delivery delivery = request.toEntity();
        List<DeliveryRoute> routes = createRoutes(delivery);

        deliveryRepository.save(delivery);
        deliveryRouteRepository.saveAll(routes);

        return new DeliveryCreateResult(
                DeliveryCreateResponse.from(delivery),
                true
        );
    }

    /**
     * 허브 서비스를 조회해 배송 경로를 생성하는 private 메서드
     * @param delivery 배송 객체
     * @return 배송 경로
     */
    private List<DeliveryRoute> createRoutes(Delivery delivery){
        List<DeliveryRoute> routes = new ArrayList<>();

        UUID departureHubId = delivery.getDepartureHubId();
        UUID destinationHubId = delivery.getDestinationHubId();

        // 출발 허브와 도착 허브가 다르면 허브 -> 허브 구간을 먼저 생성
        if(!departureHubId.equals(destinationHubId)){
            HubRouteResponse hubRoute = hubClient.getRoute(departureHubId, destinationHubId);

            // 허브 서비스에서 가져온 정보 검증
            if(hubRoute == null || hubRoute.distance() == null || hubRoute.duration() == null){
                throw new IllegalStateException("허브 간 예상 거리와 예상 소요 시간이 필요합니다.");
            }

            if (!departureHubId.equals(hubRoute.originHubId())
                    || !destinationHubId.equals(hubRoute.destinationHubId())) {
                throw new IllegalStateException(
                        "조회된 허브 경로가 요청한 출발, 목적지와 다릅니다."
                );
            }

            if (hubRoute.distance().signum() < 0
                    || hubRoute.duration() < 0) {
                throw new IllegalStateException(
                        "허브 간 거리와 소요시간은 음수일 수 없습니다."
                );
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

        // 모든 배송의 마지막 구간은 허브 -> 업체
        DeliveryRoute hubToCompany = DeliveryRoute.builder()
                .delivery(delivery)
                .sequence(routes.size() + 1)
                .departureHubId(destinationHubId)
                .type(RouteType.HUB_TO_COMPANY)
                .build();

        routes.add(hubToCompany);
        return routes;
    }

    /**
     * 배송 취소
     * 배송을 취소하는 경우 해당 배송과 배송 경로를 함께 취소
     * 배송이 없는 경우 : 404
     * 배송이 이미 논리 삭제된 경우 : 404
     * 배송을 취소할 수 없는 경우 : 409
     * @param deliveryId
     * @return 배송 취소 응답
     */
    @Override
    @Transactional
    public DeliveryCancelResponse cancelDelivery(UUID deliveryId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 배송을 찾을 수 없습니다."));

        if(delivery.getDeletedAt() != null){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 배송을 찾을 수 없습니다.");
        }

        // 이미 취소된 배송에 대해 반복 취소 요청이 오는 경우 기존 결과를 응답
        if(delivery.getStatus() == DeliveryStatus.CANCELLED){
            return DeliveryCancelResponse.from(delivery);
        }

        List<DeliveryRoute> routes = deliveryRouteRepository.findAllByDeliveryIdOrderBySequenceAsc(deliveryId);

        try{
            delivery.cancel();

            for(DeliveryRoute route : routes){
                route.cancel();
            }
        }
        // 배송, 배송 경로 상태가 WAITING인 경우/배송이 이미 취소된 경우를 제외한 취소 불가 상태
        catch (IllegalStateException e){
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }

        return DeliveryCancelResponse.from(delivery);
    }

    /**
     * 배송 삭제
     * 배송을 삭제하는 경우 이미 취소된 배송에 한해 배송과 배송 경로를 삭제
     * @param deliveryId
     */
    @Override
    @Transactional
    public void deleteDelivery(UUID deliveryId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 배송을 찾을 수 없습니다."));

        if(delivery.getDeletedAt() != null) return;

        // 배송 상태가 삭제 가능한지 검증
        try{
            delivery.validateDeletable();
        } catch (IllegalStateException e){
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }

        String username = auditorAware.getCurrentAuditor()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "삭제 요청자 정보를 확인할 수 없습니다."));

        List<DeliveryRoute> routes = deliveryRouteRepository.findAllByDeliveryIdOrderBySequenceAsc(deliveryId);

        LocalDateTime deletedAt = LocalDateTime.now();

        delivery.delete(username, deletedAt);

        for(DeliveryRoute route : routes){
            route.delete(username, deletedAt);
        }

    }
}

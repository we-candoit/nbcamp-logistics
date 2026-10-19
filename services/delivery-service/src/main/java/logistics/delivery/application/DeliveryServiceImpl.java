package logistics.delivery.application;

import feign.FeignException;
import logistics.delivery.domain.entity.Delivery;
import logistics.delivery.domain.entity.DeliveryRoute;
import logistics.delivery.domain.enums.DeliveryRouteStatus;
import logistics.delivery.domain.enums.DeliveryStatus;
import logistics.delivery.domain.enums.RouteType;
import logistics.delivery.infrastructure.client.HubClient;
import logistics.delivery.infrastructure.client.OrderClient;
import logistics.delivery.infrastructure.client.UserClient;
import logistics.delivery.infrastructure.client.dto.HubRouteResponse;
import logistics.delivery.infrastructure.client.dto.OrderDetailResponse;
import logistics.delivery.infrastructure.client.dto.UserDetailResponse;
import logistics.delivery.infrastructure.repository.DeliveryRepository;
import logistics.delivery.infrastructure.repository.DeliveryRouteRepository;
import logistics.delivery.infrastructure.repository.DeliverySpecification;
import logistics.delivery.presentation.dto.request.DeliveryAssignRequest;
import logistics.delivery.presentation.dto.request.DeliveryCreateRequest;
import logistics.delivery.presentation.dto.request.DeliverySearchRequest;
import logistics.delivery.presentation.dto.request.DeliveryStatusUpdateRequest;
import logistics.delivery.presentation.dto.response.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
    private final AuditorAware<String> auditorAware;
    private final HubClient hubClient;
    private final OrderClient orderClient;
    private final UserClient userClient;


    /**
     * 배송 목록 조회 및 검색
     * @param request 검색 조건
     * @param pageable 페이징 처리 정보
     * @param userId 배송 조회 범위를 확인할 요청자의 사용자ID
     * @param role 권한
     * @return 검색 조건, 조회 권한을 만족하는 배송 목록을 페이징 처리해서 반환
     */
    @Override
    public Page<DeliverySearchResponse> searchDeliveries(
            DeliverySearchRequest request,
            Pageable pageable,
            UUID userId,
            String role
    ) {
        if(!"MASTER".equals(role) && !"USER".equals(role)){
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "허용되지 않은 권한입니다,");
        }

        if(userId == null){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "요청자 정보를 확인할 수 없습니다.");
        }

        Specification<Delivery> specification =
                DeliverySpecification.isNotDeleted()
                        .and(DeliverySpecification.hasStatus(request.status()))
                        .and(DeliverySpecification.hasDepartureHubId(
                                request.departureHubId()
                        ))
                        .and(DeliverySpecification.hasDestinationHubId(
                                request.destinationHubId()
                        ))
                        // 검색 조건으로 입력한 담당자가 담당하는 배송으로 검색
                        .and(DeliverySpecification.hasManagerId(request.managerId()))
                        .and(DeliverySpecification.hasOrderId(request.orderId()));

        // USER는 본인이 배송 또는 배송 경로 담당자인 배송만 조회
        if ("USER".equals(role)) {
            specification = specification.and(
                    DeliverySpecification.hasManagerId(userId)
            );
        }

        return deliveryRepository.findAll(specification, pageable)
                .map(DeliverySearchResponse::from);
    }

    /**
     * 배송 상세 조회
     * @param deliveryId 조회할 배송
     * @param userId 배송 담당자, 주문자(요청 업체)와 비교할 요청자 ID
     * @param role MASTER의 전체 조회 권한 확인
     * @return
     */
    @Override
    public DeliveryDetailResponse getDeliveryDetail(UUID deliveryId, UUID userId, String username, String role) {

        Delivery delivery = getAccessibleDelivery(deliveryId, userId, username, role);

        return DeliveryDetailResponse.from(delivery);
    }

    /**
     * 배송을 조회하고 요청자의 조회 권한을 검증하는 private 메서드
     * 배송 상세 조회, 배송 경로 조회에서 공통으로 사용
     * @param deliveryId 조회할 배송
     * @param userId 배송 담당자, 주문자(요청 업체)와 비교할 요청자 ID
     * @param role MASTER의 전체 조회 권한 확인
     * @return
     */
    private Delivery getAccessibleDelivery(UUID deliveryId, UUID userId, String username, String role) {

        // 조회 권한 검증
        if(!"MASTER".equals(role) && !"USER".equals(role)){
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "허용되지 않은 권한입니다.");
        }

        Delivery delivery = deliveryRepository.findByIdAndDeletedAtIsNull(deliveryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "배송을 찾을 수 없습니다."));

        // MASTER, 배송 또는 배송 경로 담당자는 바로 조회 허용
        if("MASTER".equals(role) || isDeliveryManager(delivery, userId)){
            return delivery;
        }

        // 나머지 사용자는 해당 배송의 주문자인지 확인
        OrderDetailResponse order;

        try{
            order = orderClient.getOrderDetail(
                    delivery.getOrderId(),
                    userId,
                    username,
                    role
            );
        } catch (FeignException e){
            if(e.status() == 403 || e.status() == 404){
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "배송을 찾을 수 없습니다.");
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "주문자 정보를 확인할 수 없습니다.", e);
        }
        if(order == null
                || order.orderUserId() == null
                || !delivery.getOrderId().equals(order.orderId())){
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "주문 서비스의 응답을 확인할 수 없습니다.");
        }

        // 조회 권한이 없는 사용자에게 배송 존재여부를 숨기기 위해 404로 통일
        if (!userId.equals(order.orderUserId())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "배송을 찾을 수 없습니다."
            );
        }

        return delivery;
    }

    /**
     * 배송 상세 조회 요청자가 배송 담당자 또는 배송 경로 담당자 인지 확인하는 private 메서드
     * @param delivery 조회 대상 배송
     * @param userId 조회를 요청한 사용자 id
     * @return 배송 담당 여부
     */
    private boolean isDeliveryManager(Delivery delivery, UUID userId){

        if(userId.equals(delivery.getManagerId())) return true;

        return deliveryRouteRepository
                .existsByDeliveryIdAndManagerIdAndDeletedAtIsNull(delivery.getId(), userId);
    }

    /**
     * 배송 경로 조회
     * @param deliveryId 조회할 배송
     * @param userId 배송 담당자, 주문자(요청 업체)와 비교할 요청자 ID
     * @param role MASTER의 전체 조회 권한 확인
     * @return
     */
    @Override
    public List<DeliveryRouteResponse> getDeliveryRoutes(UUID deliveryId, UUID userId, String username, String role) {
        Delivery delivery = getAccessibleDelivery(deliveryId, userId, username, role);
        return deliveryRouteRepository.findAllByDeliveryIdAndDeletedAtIsNullOrderBySequenceAsc(delivery.getId())
                .stream()
                .map(DeliveryRouteResponse::from)
                .toList();
    }

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
     * @param delivery 생성 대상 배송
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
     * @param deliveryId 취소 대상 배송
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

    /**
     * 배송 담당자 배정
     * @param deliveryId 담당자를 배정할 배송
     * @param routeId 담당자를 배정할 배송 경로
     * @param request 배정할 담당자 ID가 있는 요청
     * @param requesterId 배송 담당자 배정을 요청하는 MASTER의 사용자ID
     * @param role 배정 요청자의 권한
     * @return 배송 담당자 배정 응답
     */
    @Override
    @Transactional
    public DeliveryAssignResponse assignDeliveryManager(
            UUID deliveryId, UUID routeId, DeliveryAssignRequest request, UUID requesterId, String role) {

        if(!"MASTER".equals(role)){
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "배송 담당자 배정은 MASTER만 가능합니다.");
        }

        if(requesterId == null){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "요청자 정보를 확인할 수 없습니다.");
        }

        Delivery delivery = deliveryRepository.findByIdAndDeletedAtIsNull(deliveryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "배송을 찾을 수 없습니다."));

        DeliveryRoute route = deliveryRouteRepository.findByIdAndDeliveryIdAndDeletedAtIsNull(routeId, deliveryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "배송 경로를 찾을 수 없습니다."));

        try {
            delivery.validateManagerAssignable();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }

        if (route.getStatus() != DeliveryRouteStatus.WAITING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "대기 중인 배송 경로에만 담당자를 배정할 수 있습니다.");
        }

        boolean sameRouteManager = request.managerId().equals(route.getManagerId());
        boolean companyRoute = route.getType() == RouteType.HUB_TO_COMPANY;
        boolean sameDeliveryManager = request.managerId().equals(delivery.getManagerId());

        // 이미 담당자가 배정되어있고, 동일한 담당자로 재요청시 사용자API 호출 생략, 기존 결과 반환
        if (sameRouteManager && (!companyRoute || sameDeliveryManager)) {
            return DeliveryAssignResponse.from(route);
        }

        validateDeliveryManager(
                request.managerId(),
                route.getDepartureHubId(),
                requesterId,
                role
        );

        try {
            route.assignManager(request.managerId());

            if (route.getType() == RouteType.HUB_TO_COMPANY) {
                delivery.assignManager(request.managerId());
            }
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }

        return DeliveryAssignResponse.from(route);
    }

    /**
     * 사용자 API를 호출해 배송 담당자 자격과 허브 소속을 검증하는 private 메서드
     * @param managerId 베정할 배송 담당자의 사용자ID
     * @param departureHubId 배정할 배송 경로의 출발 허브ID
     * @param requesterId 배정을 요청한 MASTER의 사용자ID
     * @param role 배정 요청자 권한
     */
    private void validateDeliveryManager(UUID managerId, UUID departureHubId, UUID requesterId, String role) {
        UserDetailResponse user;

        try {
            user = userClient.getUserDetail(managerId, requesterId, role);
        } catch (FeignException e) {
            if (e.status() == 404) {
                throw new ResponseStatusException(
                        HttpStatus.valueOf(422), "배정할 사용자를 찾을 수 없습니다.");
            }

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "사용자 정보를 확인할 수 없습니다.", e);
        }

        if (user == null
                || !managerId.equals(user.userId())
                || user.isDeliveryManager() == null
                || user.affiliationType() == null
                || user.affiliationStatus() == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "사용자 서비스의 응답을 확인할 수 없습니다.");
        }

        if (!Boolean.TRUE.equals(user.isDeliveryManager())
                || !"HUB".equals(user.affiliationType())
                || !"CONFIRMED".equals(user.affiliationStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.valueOf(422), "소속이 확정된 허브 배송 담당자만 배정할 수 있습니다."
            );
        }

        if (user.hubId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "사용자의 소속 허브 정보를 확인할 수 없습니다."
            );
        }

        if (!departureHubId.equals(user.hubId())) {
            throw new ResponseStatusException(
                    HttpStatus.valueOf(422), "배송 경로의 출발 허브에 소속된 담당자만 배정할 수 있습니다."
            );
        }
    }

    /**
     * 배송 상태 변경
     * 요청자의 변경 권한과 배송 진행 순서를 검증한 뒤 배송 경로 상태 변경
     * 실제 변경 발생 시 배송 전체 상태도 같은 트랜잭션으로 처리
     * @param deliveryId 상태 변경 대상 배송ID
     * @param routeId 상태 변경 대상 배송경로ID
     * @param request 변경할 배송 경로 상태
     * @param requesterId 상태 변경을 요청한 사용자ID
     * @param role 요청 사용자 권한
     * @return 배송 경로 상태, 배송 전체 상태
     */
    @Override
    @Transactional
    public DeliveryStatusUpdateResponse updateDeliveryStatus(
            UUID deliveryId, UUID routeId, DeliveryStatusUpdateRequest request, UUID requesterId, String role) {
        if (!"MASTER".equals(role) && !"USER".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "허용되지 않은 권한입니다.");
        }

        if (requesterId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "요청자 정보를 확인할 수 없습니다.");
        }

        Delivery delivery = deliveryRepository.findByIdAndDeletedAtIsNull(deliveryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "배송을 찾을 수 없습니다."));

        DeliveryRoute route = deliveryRouteRepository.findByIdAndDeliveryIdAndDeletedAtIsNull(routeId, deliveryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "배송 경로를 찾을 수 없습니다."));

        if (!"MASTER".equals(role) && !requesterId.equals(route.getManagerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "해당 배송 경로의 담당자만 상태를 변경할 수 있습니다."
            );
        }

        if (delivery.getStatus() == DeliveryStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "취소된 배송은 상태를 변경할 수 없습니다.");
        }

        try {
            if (route.getStatus() == DeliveryRouteStatus.WAITING
                    && request.status() == DeliveryRouteStatus.IN_TRANSIT) {
                validatePreviousRoutesArrived(delivery, route);
            }

            boolean changed = route.updateStatus(request.status());

            if (changed) {
                delivery.updateStatusByRoute(route.getType(), route.getStatus());
            }
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }

        return DeliveryStatusUpdateResponse.from(delivery, route);
    }

    /**
     * 출발 배송 경로보다 이전 배송 경로가 모두 도착했는지 검증하는 private 메서드
     * 배송 경로의 1구간은 이전 배송 경로가 없으므로 생략
     * @param delivery 상태 변경 대상 배송
     * @param route 출발하려는 배송 경로
     */
    private void validatePreviousRoutesArrived(Delivery delivery, DeliveryRoute route){
        // 첫 번째 경로는 이전 경로 상태 검사 생략
        if (route.getSequence() == 1) return;

        List<DeliveryRoute> previousRoutes = deliveryRouteRepository
                .findAllByDeliveryIdAndDeletedAtIsNullOrderBySequenceAsc(delivery.getId())
                .stream()
                .filter(previous -> previous.getSequence() < route.getSequence())
                .toList();

        if(previousRoutes.isEmpty()){
            throw new IllegalStateException("이전 배송 경로를 확인할 수 없습니다.");
        }

        boolean allArrived = previousRoutes.stream()
                .allMatch(previous -> previous.getStatus() == DeliveryRouteStatus.ARRIVED);

        if(!allArrived){
            throw new IllegalStateException("이전 배송 경로가 모두 도착해야 출발할 수 있습니다.");
        }
    }
}

package logistics.delivery.application;

import logistics.delivery.common.exception.DeliveryErrorCode;
import logistics.delivery.common.exception.DeliveryException;
import logistics.delivery.domain.entity.Delivery;
import logistics.delivery.domain.entity.DeliveryRoute;
import logistics.delivery.domain.enums.DeliveryRouteStatus;
import logistics.delivery.domain.enums.DeliveryStatus;
import logistics.delivery.domain.enums.RouteType;
import logistics.delivery.infrastructure.client.HubClientAdapter;
import logistics.delivery.infrastructure.client.OrderClientAdapter;
import logistics.delivery.infrastructure.client.UserClientAdapter;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final AuditorAware<UUID> auditorAware;
    private final HubClientAdapter hubClientAdapter;
    private final OrderClientAdapter orderClientAdapter;
    private final UserClientAdapter userClientAdapter;

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
            throw new DeliveryException(DeliveryErrorCode.ACCESS_DENIED);
        }

        if(userId == null){
            throw new DeliveryException(DeliveryErrorCode.REQUESTER_REQUIRED);
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
    public DeliveryDetailResponse getDeliveryDetail(UUID deliveryId, UUID userId, String role) {

        Delivery delivery = getAccessibleDelivery(deliveryId, userId, role);

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
    private Delivery getAccessibleDelivery(UUID deliveryId, UUID userId, String role) {

        // 조회 권한 검증
        if(!"MASTER".equals(role) && !"USER".equals(role)){
            throw new DeliveryException(DeliveryErrorCode.ACCESS_DENIED);
        }

        Delivery delivery = deliveryRepository.getNotDeletedById(deliveryId);

        // MASTER, 배송 또는 배송 경로 담당자는 바로 조회 허용
        if("MASTER".equals(role) || isDeliveryManager(delivery, userId)){
            return delivery;
        }

        // 나머지 사용자는 해당 배송의 주문자인지 확인
        OrderDetailResponse order = orderClientAdapter.getOrderForDeliveryAccess(
                delivery.getOrderId(), userId, role);

        // 조회 권한이 없는 사용자에게 배송 존재여부를 숨기기 위해 404로 통일
        if (!userId.equals(order.orderUserId())) {
            throw new DeliveryException(DeliveryErrorCode.DELIVERY_NOT_FOUND);
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
    public List<DeliveryRouteResponse> getDeliveryRoutes(UUID deliveryId, UUID userId, String role) {
        Delivery delivery = getAccessibleDelivery(deliveryId, userId, role);
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
                throw new DeliveryException(DeliveryErrorCode.DELETED_DELIVERY_RECREATION_NOT_ALLOWED);
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
            HubRouteResponse hubRoute = hubClientAdapter.getRoute(departureHubId, destinationHubId);

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

        Delivery delivery = deliveryRepository.getByIdIncludingDeleted(deliveryId);

        if(delivery.getDeletedAt() != null){
            throw new DeliveryException(DeliveryErrorCode.DELIVERY_NOT_FOUND);
        }

        // 이미 취소된 배송에 대해 반복 취소 요청이 오는 경우 기존 결과를 응답
        if(delivery.getStatus() == DeliveryStatus.CANCELLED){
            return DeliveryCancelResponse.from(delivery);
        }

        List<DeliveryRoute> routes = deliveryRouteRepository.findAllByDeliveryIdOrderBySequenceAsc(deliveryId);

        delivery.cancel();

        for(DeliveryRoute route : routes){
            route.cancel();
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

        Delivery delivery = deliveryRepository.getByIdIncludingDeleted(deliveryId);

        if(delivery.getDeletedAt() != null) return;

        // 배송 상태가 삭제 가능한지 검증
        delivery.validateDeletable();

        UUID userId = auditorAware.getCurrentAuditor()
                .orElseThrow(() -> new DeliveryException(DeliveryErrorCode.REQUESTER_REQUIRED));

        List<DeliveryRoute> routes = deliveryRouteRepository.findAllByDeliveryIdOrderBySequenceAsc(deliveryId);

        LocalDateTime deletedAt = LocalDateTime.now();

        delivery.delete(userId, deletedAt);

        for(DeliveryRoute route : routes){
            route.delete(userId, deletedAt);
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
            throw new DeliveryException(DeliveryErrorCode.MANAGER_ASSIGNMENT_FORBIDDEN);
        }

        if(requesterId == null){
            throw new DeliveryException(DeliveryErrorCode.REQUESTER_REQUIRED);
        }

        Delivery delivery = deliveryRepository.getNotDeletedById(deliveryId);

        DeliveryRoute route = deliveryRouteRepository.getNotDeletedByIdAndDeliveryId(routeId, deliveryId);

        delivery.validateManagerAssignable();

        if (route.getStatus() != DeliveryRouteStatus.WAITING) {
            throw new DeliveryException(DeliveryErrorCode.DELIVERY_ROUTE_ASSIGNMENT_NOT_ALLOWED);
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

        route.assignManager(request.managerId());

        if (route.getType() == RouteType.HUB_TO_COMPANY) {
            delivery.assignManager(request.managerId());
        }

        return DeliveryAssignResponse.from(route);
    }

    /**
     * 사용자 API를 호출해 배송 담당자 자격과 허브 소속을 검증하는 private 메서드
     * @param managerId 배정할 배송 담당자의 사용자ID
     * @param departureHubId 배정할 배송 경로의 출발 허브ID
     * @param requesterId 배정을 요청한 MASTER의 사용자ID
     * @param role 배정 요청자 권한
     */
    private void validateDeliveryManager(UUID managerId, UUID departureHubId, UUID requesterId, String role) {
        UserDetailResponse user = userClientAdapter.getAssignmentCandidate(managerId, requesterId, role);

        if (!Boolean.TRUE.equals(user.isDeliveryManager())
                || !"HUB".equals(user.affiliationType())
                || !"CONFIRMED".equals(user.affiliationStatus())) {
            throw new DeliveryException(DeliveryErrorCode.ASSIGNEE_NOT_ELIGIBLE);
        }

        if (user.hubId() == null) {
            throw new DeliveryException(DeliveryErrorCode.INVALID_USER_SERVICE_RESPONSE);
        }

        if (!departureHubId.equals(user.hubId())) {
            throw new DeliveryException(DeliveryErrorCode.ASSIGNEE_HUB_MISMATCH);
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
            throw new DeliveryException(DeliveryErrorCode.ACCESS_DENIED);
        }

        if (requesterId == null) {
            throw new DeliveryException(DeliveryErrorCode.REQUESTER_REQUIRED);
        }

        Delivery delivery = deliveryRepository.getNotDeletedById(deliveryId);

        DeliveryRoute route = deliveryRouteRepository.getNotDeletedByIdAndDeliveryId(routeId, deliveryId);

        if (!"MASTER".equals(role) && !requesterId.equals(route.getManagerId())) {
            throw new DeliveryException(DeliveryErrorCode.ROUTE_STATUS_UPDATE_FORBIDDEN);
        }

        if (delivery.getStatus() == DeliveryStatus.CANCELLED) {
            throw new DeliveryException(DeliveryErrorCode.CANCELLED_DELIVERY_STATUS_CHANGE);
        }

        if (route.getStatus() == DeliveryRouteStatus.WAITING
                && request.status() == DeliveryRouteStatus.IN_TRANSIT) {
            validatePreviousRoutesArrived(delivery, route);
        }

        boolean changed = route.updateStatus(request.status());

        if (changed) {
            delivery.updateStatusByRoute(route.getType(), route.getStatus());
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
        // 배송 경로의 1구간은 이전 배송 경로 상태 검사 생략
        if (route.getSequence() == 1) return;

        List<DeliveryRoute> previousRoutes = deliveryRouteRepository
                .findAllByDeliveryIdAndDeletedAtIsNullOrderBySequenceAsc(delivery.getId())
                .stream()
                .filter(previous -> previous.getSequence() < route.getSequence())
                .toList();

        if(previousRoutes.isEmpty()){
            throw new DeliveryException(DeliveryErrorCode.PREVIOUS_ROUTES_NOT_FOUND);
        }

        boolean allArrived = previousRoutes.stream()
                .allMatch(previous -> previous.getStatus() == DeliveryRouteStatus.ARRIVED);

        if(!allArrived){
            throw new DeliveryException(DeliveryErrorCode.PREVIOUS_ROUTES_NOT_ARRIVED);
        }
    }
}

package logistics.delivery.infrastructure.repository;

import logistics.delivery.common.exception.DeliveryErrorCode;
import logistics.delivery.common.exception.DeliveryException;
import logistics.delivery.domain.entity.DeliveryRoute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryRouteRepository extends JpaRepository<DeliveryRoute, UUID> {

    // 배송에 속한 배송 경로를 조회하는 메서드
    List<DeliveryRoute> findAllByDeliveryIdOrderBySequenceAsc(UUID deliveryId);

    // 요청자가 해당 배송의 담당자인지 확인하는 조회 메서드
    boolean existsByDeliveryIdAndManagerIdAndDeletedAtIsNull(UUID deliveryId, UUID managerId);

    // 삭제되지 않은 배송 경로를 순서대로 조회하는 메스드
    List<DeliveryRoute> findAllByDeliveryIdAndDeletedAtIsNullOrderBySequenceAsc(UUID deliveryId);

    // 담당자를 배정할 경로가 해당 배송에 속하고, 배송 경로가 삭제되지 않았는지 확인하는 조회 메서드
    Optional<DeliveryRoute> findByIdAndDeliveryIdAndDeletedAtIsNull(UUID routeId, UUID deliveryId);

    // 배송에 속한 삭제되지 않은 배송 경로를 조회(취소된 배송경로도 포함), 배송 존재/삭제는 서비스에서 확인
    default DeliveryRoute getNotDeletedByIdAndDeliveryId(UUID routeId, UUID deliveryId) {
        return findByIdAndDeliveryIdAndDeletedAtIsNull(routeId, deliveryId)
                .orElseThrow(() -> new DeliveryException(DeliveryErrorCode.DELIVERY_ROUTE_NOT_FOUND));
    }

}

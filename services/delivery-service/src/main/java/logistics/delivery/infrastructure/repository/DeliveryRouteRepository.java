package logistics.delivery.infrastructure.repository;

import logistics.delivery.domain.entity.DeliveryRoute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeliveryRouteRepository extends JpaRepository<DeliveryRoute, UUID> {

    // 배송에 속한 배송 경로를 조회하는 메서드
    List<DeliveryRoute> findAllByDeliveryIdOrderBySequenceAsc(UUID deliveryId);

    // 요청자가 해당 배송의 담당자인지 확인하는 조회 메서드
    boolean existsByDeliveryIdAndManagerIdAndDeletedAtIsNull(UUID deliveryId, UUID managerId);

    // 삭제되지 않은 배송 경로를 순서대로 조회하는 메스드
    List<DeliveryRoute> findAllByDeliveryIdAndDeletedAtIsNullOrderBySequenceAsc(UUID deliveryId);
}

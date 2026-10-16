package logistics.delivery.infrastructure.repository;

import logistics.delivery.domain.entity.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {

    // 주문id로 기존에 생성된 배송 조회 메서드
    Optional<Delivery> findByOrderId(UUID orderId);
}

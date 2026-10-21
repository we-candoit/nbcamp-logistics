package logistics.delivery.infrastructure.repository;

import jakarta.persistence.LockModeType;
import logistics.delivery.domain.entity.DeliveryOrderControl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DeliveryOrderControlRepository extends JpaRepository<DeliveryOrderControl, UUID> {

    // 주문별 관리 기록 존재 여부 확인
    boolean existsByOrderId(UUID orderId);

    // 주문별 관리 기록 조호 후 쓰기 락 획득 -> 같은 기록을 잠그려는 다른 트랜잭션은 잠금이 해제될떄까지 대기
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select control 
            from DeliveryOrderControl control
            where control.orderId = :orderId
            """)
    Optional<DeliveryOrderControl> findByOrderIdForUpdate(@Param("orderId") UUID orderId);
}

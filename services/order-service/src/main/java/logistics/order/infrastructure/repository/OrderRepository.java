package logistics.order.infrastructure.repository;

import logistics.order.domain.entity.Order;
import logistics.order.domain.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    public Optional<Order> findByIdAndDeletedAtIsNull(UUID orderId);

    //기준시각 이전에 생성되어 아직 WAITING인 주문ID / 주문생성 중 서버가 종료되어 보상이 실행되지 않은 주문
    @Query("select o.id from Order o " +
            "where o.status = :status and o.createdAt < :threshold and o.deletedAt is null " +
            "order by o.createdAt asc")
    List<UUID> findIdsByStatusCreatedBefore(@Param("status") OrderStatus status,
                                            @Param("threshold") LocalDateTime threshold);

    //현재 상태가 from일 때만 to로 변경 / 조회 후 다른 요청이 상태를 바꿨으면 변경하지 않음 (변경된 행 수 반환)
    //벌크 UPDATE는 감사 정보가 자동으로 채워지지 않아 직접 지정
    @Modifying(clearAutomatically = true)
    @Query("update Order o set o.status = :to, o.updatedAt = :updatedAt, o.updatedBy = :updatedBy " +
            "where o.id = :orderId and o.status = :from")
    int updateStatusIfCurrent(@Param("orderId") UUID orderId,
                              @Param("from") OrderStatus from,
                              @Param("to") OrderStatus to,
                              @Param("updatedAt") LocalDateTime updatedAt,
                              @Param("updatedBy") UUID updatedBy);
}

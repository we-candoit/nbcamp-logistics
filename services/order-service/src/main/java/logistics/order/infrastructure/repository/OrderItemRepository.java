package logistics.order.infrastructure.repository;

import logistics.order.domain.entity.OrderItem;
import logistics.order.domain.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    //주문 ID로 주문상품 조회 / 주문까지 fetch join (주문 1개 = 주문상품 1개)
    @Query("select oi from OrderItem oi join fetch oi.order o " +
            "where o.id = :orderId and o.deletedAt is null")
    Optional<OrderItem> findWithOrder(@Param("orderId") UUID orderId);

    //===== 주문 목록 조회 / 삭제되지 않은 주문만, 최신순 =====
    //null 파라미터 비교(:status is null)는 PostgreSQL에서 타입 추론 오류가 날 수 있어 조건별로 분리

    //전체 주문
    @Query("select oi from OrderItem oi join fetch oi.order o " +
            "where o.deletedAt is null order by o.createdAt desc")
    List<OrderItem> findAllWithOrder();

    //특정 상태의 주문
    @Query("select oi from OrderItem oi join fetch oi.order o " +
            "where o.orderStatus = :status and o.deletedAt is null order by o.createdAt desc")
    List<OrderItem> findAllWithOrder(@Param("status") OrderStatus status);

    //특정 유저의 주문
    @Query("select oi from OrderItem oi join fetch oi.order o " +
            "where o.orderUserId = :orderUserId and o.deletedAt is null order by o.createdAt desc")
    List<OrderItem> findAllWithOrder(@Param("orderUserId") UUID orderUserId);

    //특정 유저의 특정 상태 주문
    @Query("select oi from OrderItem oi join fetch oi.order o " +
            "where o.orderUserId = :orderUserId and o.orderStatus = :status and o.deletedAt is null " +
            "order by o.createdAt desc")
    List<OrderItem> findAllWithOrder(@Param("orderUserId") UUID orderUserId,
                                                           @Param("status") OrderStatus status);
}

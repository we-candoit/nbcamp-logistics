package logistics.order.infrastructure.repository;

import logistics.order.domain.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    //주문 ID로 주문상품 조회 / 주문까지 fetch join (주문 1개 = 주문상품 1개)
    @Query("select oi from OrderItem oi join fetch oi.order o where o.id = :orderId")
    Optional<OrderItem> findWithOrderByOrderId(@Param("orderId") UUID orderId);
}

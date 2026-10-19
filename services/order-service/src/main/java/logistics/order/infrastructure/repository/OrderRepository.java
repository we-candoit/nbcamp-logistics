package logistics.order.infrastructure.repository;

import logistics.order.domain.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    public Optional<Order> findByIdAndDeletedAtIsNull(UUID orderId);
}

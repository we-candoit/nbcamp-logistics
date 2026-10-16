package logistics.delivery.infrastructure.repository;

import logistics.delivery.domain.entity.DeliveryRoute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeliveryRouteRepository extends JpaRepository<DeliveryRoute, UUID> {

}

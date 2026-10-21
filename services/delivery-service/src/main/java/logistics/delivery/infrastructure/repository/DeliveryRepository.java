package logistics.delivery.infrastructure.repository;

import logistics.delivery.common.exception.DeliveryErrorCode;
import logistics.delivery.common.exception.DeliveryException;
import logistics.delivery.domain.entity.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID>, JpaSpecificationExecutor<Delivery> {

    // 주문id로 기존에 생성된 배송 조회 메서드
    Optional<Delivery> findByOrderId(UUID orderId);

    // 삭제되지 않은 배송만 조회하는 메서드
    Optional<Delivery> findByIdAndDeletedAtIsNull(UUID id);

    // 삭제되지 않은 배송 조회(취소된 배송도 조회 대상에 포함됨)
    default Delivery getNotDeletedById(UUID deliveryId) {
        return findByIdAndDeletedAtIsNull(deliveryId)
                .orElseThrow(() -> new DeliveryException(DeliveryErrorCode.DELIVERY_NOT_FOUND));
    }

    // 삭제 여부랑 상관없이 배송 조회 -> 삭제된 배송 처리는 서비스에서 결정
    default Delivery getByIdIncludingDeleted(UUID deliveryId) {
        return findById(deliveryId)
                .orElseThrow(() -> new DeliveryException(DeliveryErrorCode.DELIVERY_NOT_FOUND));
    }

    // 삭제 여부랑 상관없이 주문ID로 배송 조회 -> 삭제된 배송 처리는 서비스에서 결정
    default Delivery getByOrderIdIncludingDeleted(UUID orderId){
        return findByOrderId(orderId)
                .orElseThrow(() -> new DeliveryException(DeliveryErrorCode.DELIVERY_NOT_FOUND));
    }
}

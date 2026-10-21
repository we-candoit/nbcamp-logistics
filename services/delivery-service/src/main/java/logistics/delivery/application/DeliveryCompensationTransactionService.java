package logistics.delivery.application;

import logistics.delivery.common.exception.DeliveryErrorCode;
import logistics.delivery.common.exception.DeliveryException;
import logistics.delivery.domain.entity.Delivery;
import logistics.delivery.domain.entity.DeliveryOrderControl;
import logistics.delivery.domain.entity.DeliveryRoute;
import logistics.delivery.domain.enums.DeliveryStatus;
import logistics.delivery.infrastructure.repository.DeliveryRepository;
import logistics.delivery.infrastructure.repository.DeliveryRouteRepository;
import logistics.delivery.presentation.dto.response.DeliveryCancelResponse;
import logistics.delivery.presentation.dto.response.DeliveryOrderCancelResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryCompensationTransactionService {

    private final DeliveryOrderControlManager controlManager;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryRouteRepository deliveryRouteRepository;
    private final AuditorAware<UUID> auditorAware;

    /**
     * 배송 ID로 배송과 배송 경로를 취소하고 관리 기록에 반영
     * 주문별 관리 기록의 초기화가 완료된 후 호출
     *
     * @param deliveryId 취소할 배송 ID
     * @param orderId 해당 배송의 주문 ID
     * @return 배송 취소 결과
     */
    @Transactional
    public DeliveryCancelResponse cancelByDeliveryId(UUID deliveryId, UUID orderId) {
        DeliveryOrderControl control = controlManager.getForUpdate(orderId);
        Delivery delivery = deliveryRepository.getByIdIncludingDeleted(deliveryId);

        if (!orderId.equals(delivery.getOrderId())) {
            throw new DeliveryException(DeliveryErrorCode.DELIVERY_NOT_FOUND);
        }

        cancelExistingDelivery(delivery);
        control.markCancelled();

        return DeliveryCancelResponse.from(delivery);
    }

    /**
     * 배송 ID로 취소된 배송과 배송 경로를 삭제하고 관리 기록에 반영
     * 주문별 관리 기록의 초기화가 완료된 후 호출
     *
     * @param deliveryId 삭제할 배송 ID
     * @param orderId 해당 배송의 주문 ID
     */
    @Transactional
    public void deleteByDeliveryId(UUID deliveryId, UUID orderId) {
        DeliveryOrderControl control = controlManager.getForUpdate(orderId);
        Delivery delivery = deliveryRepository.getByIdIncludingDeleted(deliveryId);

        if (!orderId.equals(delivery.getOrderId())) {
            throw new DeliveryException(DeliveryErrorCode.DELIVERY_NOT_FOUND);
        }

        deleteExistingDelivery(delivery);
        control.markCancelled();
        control.markDeleted();
    }

    /**
     * 배송 취소 또는 배송 생성 차단
     * 락을 얻어서 주문별 관리 기록을 잠그고 배송 취소 또는 생성 차단
     * 관리 기록의 초기화가 완료된 후 호출해야 함
     *
     * @param orderId 취소할 주문 ID
     * @return 기존 배송 취소 또는 배송 생성 차단 결과 응답
     */
    @Transactional
    public DeliveryOrderCancelResponse cancelByOrderId(UUID orderId) {
        DeliveryOrderControl control = controlManager.getForUpdate(orderId);

        Delivery delivery = deliveryRepository.findByOrderId(orderId)
                .orElse(null);

        if (delivery == null) {
            control.markCancelled();

            return DeliveryOrderCancelResponse.creationBlocked(orderId);
        }

        cancelExistingDelivery(delivery);
        control.markCancelled();

        return DeliveryOrderCancelResponse.cancelled(orderId, delivery.getId());
    }

    /**
     * 기존 배송, 경로를 취소
     * 삭제된 배송은 거절하고, 이미 취소된 배송은 변경하지 않음
     *
     * @param delivery 취소할 배송
     */
    private void cancelExistingDelivery(Delivery delivery) {
        if (delivery.getDeletedAt() != null) {
            throw new DeliveryException(DeliveryErrorCode.DELIVERY_NOT_FOUND);
        }

        if (delivery.getStatus() == DeliveryStatus.CANCELLED) return;

        List<DeliveryRoute> routes =
                deliveryRouteRepository.findAllByDeliveryIdOrderBySequenceAsc(delivery.getId());

        delivery.cancel();

        for (DeliveryRoute route : routes) {
            route.cancel();
        }
    }

    /**
     * 배송 삭제
     * 주문별 관리 기록 락을 얻어서 배송 삭제
     * 배송이 없어도 취소 기록이 있으면 삭제 완료로 처리
     *
     * @param orderId 삭제할 주문 ID
     */
    @Transactional
    public void deleteByOrderId(UUID orderId) {
        DeliveryOrderControl control = controlManager.getForUpdate(orderId);

        Delivery delivery = deliveryRepository.findByOrderId(orderId)
                .orElse(null);

        if (delivery != null) {
            deleteExistingDelivery(delivery);

            control.markCancelled();    // 기존 API로 취소된 배송도 관리 기록에 취소 사실을 반영한다.
        }
        control.markDeleted();
    }

    /**
     * 취소된 배송과 배송 경로를 같은 시간과 사용자로 삭제
     * 이미 삭제된 배송은 최초 삭제 기록을 유지한다.
     *
     * @param delivery 삭제할 배송
     */
    private void deleteExistingDelivery(Delivery delivery) {
        if (delivery.getDeletedAt() != null) return;

        delivery.validateDeletable();

        UUID userId = auditorAware.getCurrentAuditor()
                .orElseThrow(() -> new DeliveryException(DeliveryErrorCode.REQUESTER_REQUIRED));

        List<DeliveryRoute> routes =
                deliveryRouteRepository.findAllByDeliveryIdOrderBySequenceAsc(delivery.getId());

        LocalDateTime deletedAt = LocalDateTime.now();

        delivery.delete(userId, deletedAt);

        for (DeliveryRoute route : routes) {
            route.delete(userId, deletedAt);
        }
    }
}

package logistics.delivery.application;

import logistics.delivery.domain.entity.DeliveryOrderControl;
import logistics.delivery.infrastructure.repository.DeliveryOrderControlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryOrderControlInitializer {

    private final DeliveryOrderControlRepository controlRepository;

    // 주문별 관리 기록이 없으면 독립된 트랜잭션에서 생성
        // 동시에 생성된 경우의 중복 예외는 호출한 쪽에서 처리
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void initialize(UUID orderId){
        if(controlRepository.existsByOrderId(orderId)) return;

        DeliveryOrderControl control = DeliveryOrderControl.create(orderId);

        controlRepository.saveAndFlush(control);
    }
}

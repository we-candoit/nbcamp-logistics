package logistics.delivery.application;

import logistics.delivery.common.exception.DeliveryErrorCode;
import logistics.delivery.common.exception.DeliveryException;
import logistics.delivery.domain.entity.DeliveryOrderControl;
import logistics.delivery.infrastructure.repository.DeliveryOrderControlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryOrderControlManager {

    private final DeliveryOrderControlInitializer initializer;
    private final DeliveryOrderControlRepository controlRepository;

    // 주문별 관리 기록을 준비 - 기존 트랜잭션 밖에서
        // 동시에 다른 요청이 같은 주문의 관리 기록을 생성했다면 해당 기록을 사용하도록 중복 오류를 처리
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void prepare(UUID orderId) {
        try {
            initializer.initialize(orderId);
        } catch (DataIntegrityViolationException exception) {
            if (!isUniqueViolation(exception)
                    || !controlRepository.existsByOrderId(orderId)) {
                throw exception;
            }
        }
    }

    // 예외 원인 중 UNIQUE 제약 위반이 있는지 확인
    private boolean isUniqueViolation(Throwable exception) {
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof SQLException sqlException
                    && "23505".equals(sqlException.getSQLState())) {    // DB UNIQUE 제약 위반 코드
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    // 주문별 관리 기록을 조회하고 쓰기 락 획득 -> 호출 트랜잭션이 끝날때까지 잠금 유지
    @Transactional(propagation = Propagation.MANDATORY)
    public DeliveryOrderControl getForUpdate(UUID orderId) {
        return controlRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new DeliveryException(DeliveryErrorCode.INTERNAL_SERVER_ERROR));
    }
}
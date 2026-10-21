package logistics.order.application.compensation;

import logistics.order.domain.entity.OrderItem;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

//주문생성 진행상황 / 보상시 진행한 단계만 되돌리기 위해 기록
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class CreateOrderProgress {

    private final UUID orderId;
    private final UUID hubId;
    private final UUID itemId;
    private final Long quantity;

    private boolean stockDecreaseRequested;
    private boolean deliveryCreateRequested;

    //WAITING 주문 저장 이후 시작
    public static CreateOrderProgress start(OrderItem orderItem) {
        return new CreateOrderProgress(
                orderItem.getOrder().getId(),
                orderItem.getDepartureHubId(),
                orderItem.getItemId(),
                orderItem.getQuantity()
        );
    }

    //재고감소 요청 전에 기록 / 실패 원인(타임아웃, 재고 부족 등)과 관계없이 보상시 재고복원 대상에 포함
    public void markStockDecreaseRequested() {
        this.stockDecreaseRequested = true;
    }

    //배송생성 요청 전에 기록 / 타임아웃 등으로 응답을 못 받아도 배송이 생성됐을 수 있어 보상 대상에 포함
    public void markDeliveryCreateRequested() {
        this.deliveryCreateRequested = true;
    }
}

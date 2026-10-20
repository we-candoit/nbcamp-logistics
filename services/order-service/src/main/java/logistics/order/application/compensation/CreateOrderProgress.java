package logistics.order.application.compensation;

import logistics.order.domain.entity.OrderItem;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

//주문생성 진행상황 / 보상시 성공한 단계만 되돌리기 위해 기록
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class CreateOrderProgress {

    private final UUID orderId;
    private final UUID hubId;
    private final UUID itemId;
    private final Long quantity;

    private boolean stockDecreased;
    private UUID deliveryId;

    //WAITING 주문 저장 이후 시작
    public static CreateOrderProgress start(OrderItem orderItem) {
        return new CreateOrderProgress(
                orderItem.getOrder().getId(),
                orderItem.getDepartureHubId(),
                orderItem.getItemId(),
                orderItem.getQuantity()
        );
    }

    public void markStockDecreased() {
        this.stockDecreased = true;
    }

    public void markDeliveryCreated(UUID deliveryId) {
        this.deliveryId = deliveryId;
    }

    //배송ID가 없으면 되돌릴 배송도 없음
    public boolean isDeliveryCreated() {
        return deliveryId != null;
    }
}

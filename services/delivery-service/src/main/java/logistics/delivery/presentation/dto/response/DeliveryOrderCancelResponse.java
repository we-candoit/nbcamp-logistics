package logistics.delivery.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "주문 ID 기준 배송 취소 결과")
public record DeliveryOrderCancelResponse(

        @Schema(description = "취소를 요청한 주문 ID")
        UUID orderId,

        @Schema(description = "배송 ID, 배송이 생성되지 않았다면 null")
        UUID deliveryId,

        @Schema(description = "CANCELLED: 기존 배송 취소, CREATION_BLOCKED: 배송 없이 생성 차단")
        Result result
) {

    public enum Result {
        CANCELLED,
        CREATION_BLOCKED
    }

    // 기본 배송이 취소된 결과 응답을 생성
    public static DeliveryOrderCancelResponse cancelled(UUID orderId, UUID deliveryId) {
        return new DeliveryOrderCancelResponse(
                orderId,
                deliveryId,
                Result.CANCELLED
        );
    }

    // 배송이 없는 주문의 배송 생성을 차단한 결과를 생성
    public static DeliveryOrderCancelResponse creationBlocked(UUID orderId) {
        return new DeliveryOrderCancelResponse(
                orderId,
                null,
                Result.CREATION_BLOCKED
        );
    }
}


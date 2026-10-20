package logistics.delivery.presentation.exception;

import logistics.delivery.common.exception.DeliveryErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "배송 서비스 공통 오류 응답")
public record DeliveryErrorResponse(
        @Schema(description = "오류 식별 코드", example = "DELIVERY_NOT_FOUND")
        String code,
        @Schema(description = "오류 메시지", example = "배송을 찾을 수 없습니다.")
        String message,
        @Schema(description = "필드 검증 오류 목록, 일반 업무 오류는 빈 배열")
        List<FieldError> errors
) {

    /**
     * 필드 오류가 없는 공통 오류 응답을 생성한다.
     * @param errorCode 응답할 오류 코드
     * @return 공통 오류 응답
     */
    public static DeliveryErrorResponse from(DeliveryErrorCode errorCode) {
        return new DeliveryErrorResponse(errorCode.name(), errorCode.getMessage(), List.of());
    }

    /**
     * 필드 오류 목록의 복사본으로 검증 오류 응답을 생성한다.
     * @param errorCode 응답할 오류 코드
     * @param errors 필드별 검증 오류
     * @return 필드 오류를 포함한 응답
     */
    public static DeliveryErrorResponse of(DeliveryErrorCode errorCode, List<FieldError> errors) {
        return new DeliveryErrorResponse(errorCode.name(), errorCode.getMessage(), List.copyOf(errors));
    }

    public record FieldError(
            String field,
            String message
    ) {

    }
}

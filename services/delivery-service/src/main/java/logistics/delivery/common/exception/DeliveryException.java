package logistics.delivery.common.exception;

import lombok.Getter;

@Getter
public class DeliveryException extends RuntimeException{

    private final DeliveryErrorCode errorCode;

    // 배송 서비스 오류
    public DeliveryException(DeliveryErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    // 외부 API 호출 실패
    public DeliveryException(DeliveryErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}

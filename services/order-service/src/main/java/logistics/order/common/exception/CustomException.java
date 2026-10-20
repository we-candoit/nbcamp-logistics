package logistics.order.common.exception;

import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;

    public CustomException(ErrorCode errorCode) {
        super(errorCode.getStatus().getReasonPhrase());
        this.errorCode = errorCode;
    }

    // 그때그때 원하는 커스텀 메시지로 덮어쓰고 싶을 때
    public CustomException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}

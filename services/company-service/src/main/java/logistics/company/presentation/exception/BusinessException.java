package logistics.company.presentation.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Getter
public class BusinessException extends ResponseStatusException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(HttpStatus.valueOf(errorCode.getStatusCode()), errorCode.getMessage());   // valueOf : 404 -> HttpStatus.NOT_FOUND 형식으로 변환
        this.errorCode = errorCode;
    }
}
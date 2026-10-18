package logistics.order.presentation.exception;

import feign.FeignException;
import logistics.order.presentation.dto.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(new ErrorResponse(errorCode.getStatus().value(), e.getMessage(), LocalDateTime.now()));
    }

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ErrorResponse> handleFeignException(FeignException e) {

        //응답 자체를 받지 못한 경우(타임아웃, 연결 실패 등) status가 -1
        if (e.status() < 0) {
            log.error("외부 서비스 응답 없음", e);
            HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;

            return ResponseEntity
                    .status(status)
                    .body(new ErrorResponse(status.value(), "외부 서비스에 연결할 수 없습니다.", LocalDateTime.now()));
        }

        return ResponseEntity
                .status(e.status())
                .body(new ErrorResponse(e.status(), e.getMessage(), LocalDateTime.now()));
    }
}

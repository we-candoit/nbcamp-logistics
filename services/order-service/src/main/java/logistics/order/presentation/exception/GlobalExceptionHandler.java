package logistics.order.presentation.exception;

import feign.FeignException;
import logistics.order.presentation.dto.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

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

    //@Valid 요청값 검증 실패 / "필드명: 메시지" 형식으로 응답
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return ResponseEntity
                .status(status)
                .body(new ErrorResponse(status.value(), message, LocalDateTime.now()));
    }

    //요청 본문을 DTO로 변환 실패 (JSON 문법 오류, UUID/숫자 형식 오류, 본문 없음)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        //Jackson 메시지에 내부 클래스명이 포함되어 로그로만 남김
        log.warn("요청 본문 변환 실패: {}", e.getMessage());
        HttpStatus status = HttpStatus.BAD_REQUEST;

        return ResponseEntity
                .status(status)
                .body(new ErrorResponse(status.value(), "요청 형식이 올바르지 않습니다.", LocalDateTime.now()));
    }

    //경로변수, 쿼리파라미터 타입 변환 실패 (ex. /api/orders/abc → UUID 변환 불가)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.warn("요청 파라미터 변환 실패: {}", e.getMessage());
        HttpStatus status = HttpStatus.BAD_REQUEST;

        return ResponseEntity
                .status(status)
                .body(new ErrorResponse(status.value(), e.getName() + ": 형식이 올바르지 않습니다.", LocalDateTime.now()));
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

    //위에서 처리하지 못한 모든 예외 / 내부 메시지는 로그로만 남기고 정해진 문구로 응답
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        //Spring MVC 표준 예외(405 메서드 미지원, 404 경로 없음, 415 등)는 원래 상태코드 유지
        if (e instanceof org.springframework.web.ErrorResponse springError) {
            HttpStatusCode status = springError.getStatusCode();
            log.warn("요청 처리 실패 status={}: {}", status.value(), e.getMessage());

            return ResponseEntity
                    .status(status)
                    .body(new ErrorResponse(status.value(), "요청을 처리할 수 없습니다.", LocalDateTime.now()));
        }

        log.error("처리되지 않은 예외", e);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;

        return ResponseEntity
                .status(status)
                .body(new ErrorResponse(status.value(), "서버 내부 오류가 발생했습니다.", LocalDateTime.now()));
    }
}

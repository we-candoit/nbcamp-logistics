package logistics.user.presentation.exception;

import logistics.user.presentation.dto.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler
    public ResponseEntity<ApiResponse<Void>> customExHandler(CustomException e) {
        ApiResponse<Void> apiResponse = ApiResponse.fail(e.getMessage());
        return ResponseEntity.status(e.getErrorCode().getStatus()).body(apiResponse);
    }

    @ExceptionHandler
    public ResponseEntity<ApiResponse<Map<String, String>>> validationExHandler(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();

        List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors();
        for (FieldError fieldError : fieldErrors) {
            String fieldName = fieldError.getField();
            String defaultMessage = fieldError.getDefaultMessage();
            errors.putIfAbsent(fieldName, defaultMessage);
        }
        ApiResponse<Map<String, String>> apiResponse = ApiResponse.fail("입력값 검증에 실패했습니다.", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiResponse);
    }

    // @RequestHeader("X-User-Id")가 없을 때, 헤더 자체가 없을 때
    @ExceptionHandler
    public ResponseEntity<ApiResponse<Void>> missingHeaderExHandler(MissingRequestHeaderException e) {
        ErrorCode errorCode = ErrorCode.MISSING_USER_HEADER;
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.fail(errorCode.getMessage()));
    }

    // 헤더 값이 UUID나 MASTER/USER로 바뀌지 않을 때 (예: X-User-Id: abc), 헤더는 있으나 타입이 일치하지 않을 때
    @ExceptionHandler
    public ResponseEntity<ApiResponse<Void>> typeMismatchExHandler(MethodArgumentTypeMismatchException e) {
        ErrorCode errorCode = ErrorCode.INVALID_USER_HEADER;
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.fail(errorCode.getMessage()));
    }
}
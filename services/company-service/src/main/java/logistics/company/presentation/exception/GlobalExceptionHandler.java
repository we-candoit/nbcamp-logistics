package logistics.company.presentation.exception;

import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String UK_COMPANY_MANAGER_ACTIVE = "uk_company_manager_active";

    // 업무 예외 처리: 리소스 없음(404), 권한 없음(403) 등
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e, HttpServletRequest request) {
        return toResponse(e.getErrorCode(), request);
    }

    // 유효성 실패: @RequestBody @Valid
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException e, HttpServletRequest request) {

        List<ErrorResponse.FieldError> errors = e.getBindingResult()
                .getFieldErrors().stream()
                .map(error -> new ErrorResponse.FieldError(
                        error.getField(),
                        error.getDefaultMessage()))
                .toList();

        return toResponse(ErrorCode.BAD_REQUEST, request, errors);
    }

    // 유효성 실패: @RequestParam, @PathVariable(내부 복수 조회 ids 개수 초과 등)
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidation(HandlerMethodValidationException e, HttpServletRequest request) {

        List<ErrorResponse.FieldError> errors = e.getParameterValidationResults()                   // 파라미터별 검증 결과
                .stream()
                .flatMap(result -> result.getResolvableErrors().stream()       // 파라미터별 오류들을 한 줄로 펼침
                        .map(error -> new ErrorResponse.FieldError(            // 오류 한 건 -> FieldError로 변환
                                result.getMethodParameter().getParameterName(),                     // 파라미터 이름 (예: "ids")
                                error.getDefaultMessage())))                                        // 메시지
                .toList();

        return toResponse(ErrorCode.BAD_REQUEST, request, errors);
    }

    // 유효성 실패: Body 없음, JSON 형식 오류, Body 안의 UUID 형식 오류
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        return toResponse(ErrorCode.BAD_REQUEST, request);
    }

    // 유효성 실패: 경로·쿼리 파라미터 타입 오류 (UUID 자리에 잘못된 값 등)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        List<ErrorResponse.FieldError> errors =
                List.of(new ErrorResponse.FieldError(e.getName(), "형식이 올바르지 않습니다."));

        return toResponse(ErrorCode.BAD_REQUEST, request, errors);
    }

    // 외부 서비스 404: 요청한 데이터가 상대 서비스에 없음 (허브·사용자 등)
    @ExceptionHandler(FeignException.NotFound.class)
    public ResponseEntity<ErrorResponse> handleFeignNotFound(FeignException.NotFound e, HttpServletRequest request) {
        log.warn("External resource not found: url={}", e.request().url());
        return toResponse(ErrorCode.NOT_FOUND, request);
    }

    // 외부 호출 실패: Feign 호출 오류, 타임아웃
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ErrorResponse> handleFeignException(FeignException e, HttpServletRequest request) {
        log.error("External service call failed: status={}, message={}", e.status(), e.getMessage());
        return toResponse(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, request);
    }

    // 동시 요청으로 인한 인덱스 위반
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException e,
            HttpServletRequest request
    ) {
        // 담당자 1인 1업체 인덱스 위반 → 409
        if (isConstraintViolation(e, UK_COMPANY_MANAGER_ACTIVE)) {
            return handleBusinessException(
                    new BusinessException(ErrorCode.MANAGER_ALREADY_ASSIGNED), request);
        }
        // 그 외 무결성 위반 → 500
        return toResponse(ErrorCode.INTERNAL_SERVER_ERROR, request);
    }

    private boolean isConstraintViolation(DataIntegrityViolationException e, String constraintName) {
        return e.getCause() instanceof ConstraintViolationException cve
                && constraintName.equalsIgnoreCase(cve.getConstraintName());
    }

    private ResponseEntity<ErrorResponse> toResponse(ErrorCode errorCode, HttpServletRequest request) {
        return toResponse(errorCode, request, List.of());
    }

    private ResponseEntity<ErrorResponse> toResponse(ErrorCode errorCode, HttpServletRequest request,
                                                     List<ErrorResponse.FieldError> errors) {
        return ResponseEntity.status(errorCode.getStatusCode())
                .body(ErrorResponse.of(errorCode, request.getRequestURI(), errors));
    }
}



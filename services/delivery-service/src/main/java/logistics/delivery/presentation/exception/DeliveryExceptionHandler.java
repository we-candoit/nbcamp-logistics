package logistics.delivery.presentation.exception;

import logistics.delivery.common.exception.DeliveryErrorCode;
import logistics.delivery.common.exception.DeliveryException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.ErrorResponse;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class DeliveryExceptionHandler {

    /**
     * 배송 서비스의 예상 가능한 오류를 HTTP 상태와 공통 오류 응답으로 변환한다.
     *
     * @param exception 배송 오류 코드와 원인을 담은 예외
     * @return 오류 코드에 대응하는 HTTP 응답
     */
    @ExceptionHandler(DeliveryException.class)
    public ResponseEntity<DeliveryErrorResponse> handleDeliveryException(DeliveryException exception) {
        DeliveryErrorCode errorCode = exception.getErrorCode();
        HttpStatus status = resolveStatus(errorCode);

        // 로그
        if (status.is5xxServerError()) {
            log.error("배송 처리 실패: code={}", errorCode, exception);
        } else {
            log.warn("배송 요청 거절: code={}", errorCode);
        }

        return ResponseEntity.status(status).body(DeliveryErrorResponse.from(errorCode));
    }

    /**
     * 업무 오류 정의에 HTTP 의존성을 두지 않도록 응답 상태를 여기서 결정한다.
     *
     * @param errorCode 응답할 오류 코드
     * @return 오류에 대응하는 HTTP 상태
     */
    private HttpStatus resolveStatus(DeliveryErrorCode errorCode) {
        return switch (errorCode) {
            case INVALID_USER_ID,
                 VALIDATION_FAILED,
                 INVALID_REQUEST_BODY,
                 INVALID_PARAMETER,
                 INVALID_PAGE,
                 INVALID_PAGE_SIZE,
                 INVALID_SORT,
                 MANAGER_ID_REQUIRED,
                 HTTP_REQUEST_FAILED,
                 UNSUPPORTED_ROUTE_STATUS
                    -> HttpStatus.BAD_REQUEST;

            case USER_ID_REQUIRED,
                 ROLE_REQUIRED,
                 REQUESTER_REQUIRED
                    -> HttpStatus.UNAUTHORIZED;

            case ACCESS_DENIED,
                 MANAGER_ASSIGNMENT_FORBIDDEN,
                 ROUTE_STATUS_UPDATE_FORBIDDEN
                    -> HttpStatus.FORBIDDEN;

            case DELIVERY_NOT_FOUND,
                 DELIVERY_ROUTE_NOT_FOUND,
                 ENDPOINT_NOT_FOUND
                    -> HttpStatus.NOT_FOUND;

            case DELETED_DELIVERY_RECREATION_NOT_ALLOWED,
                 DELIVERY_CREATION_CONFLICT,
                 DELIVERY_CANCELLATION_NOT_ALLOWED,
                 DELIVERY_ROUTE_CANCELLATION_NOT_ALLOWED,
                 DELIVERY_DELETION_NOT_ALLOWED,
                 DELIVERY_ASSIGNMENT_NOT_ALLOWED,
                 DELIVERY_ROUTE_ASSIGNMENT_NOT_ALLOWED,
                 MANAGER_ALREADY_ASSIGNED,
                 CANCELLED_DELIVERY_STATUS_CHANGE,
                 DELETED_DELIVERY_STATUS_CHANGE,
                 INACTIVE_ROUTE_STATUS_CHANGE,
                 ROUTE_MANAGER_REQUIRED,
                 INVALID_ROUTE_STATUS_TRANSITION,
                 INVALID_DELIVERY_STATUS_TRANSITION,
                 PREVIOUS_ROUTES_NOT_FOUND,
                 PREVIOUS_ROUTES_NOT_ARRIVED
                    -> HttpStatus.CONFLICT;

            case ASSIGNEE_NOT_FOUND,
                 ASSIGNEE_NOT_ELIGIBLE,
                 ASSIGNEE_HUB_MISMATCH
                    -> HttpStatus.valueOf(422);

            case ORDER_SERVICE_UNAVAILABLE,
                 INVALID_ORDER_SERVICE_RESPONSE,
                 USER_SERVICE_UNAVAILABLE,
                 INVALID_USER_SERVICE_RESPONSE,
                 HUB_SERVICE_UNAVAILABLE,
                 INVALID_HUB_SERVICE_RESPONSE
                    -> HttpStatus.SERVICE_UNAVAILABLE;

            case METHOD_NOT_ALLOWED -> HttpStatus.METHOD_NOT_ALLOWED;
            case UNSUPPORTED_MEDIA_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case NOT_ACCEPTABLE -> HttpStatus.NOT_ACCEPTABLE;

            case INTERNAL_SERVER_ERROR
                    -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    /**
     * 요청 DTO의 검증 실패와 바인딩 오류를 필드별 메시지로 반환한다.
     *
     * @param exception 요청 DTO 검증 또는 바인딩 오류
     * @return 필드 오류를 포함한 HTTP 400 응답
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<DeliveryErrorResponse> handleValidationException(BindException exception) {
        List<DeliveryErrorResponse.FieldError> errors =
                exception.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .map(error -> new DeliveryErrorResponse.FieldError(
                                error.getField(),
                                error.isBindingFailure()
                                        ? "요청값의 형식이 올바르지 않습니다."
                                        : error.getDefaultMessage() == null
                                          ? "유효하지 않은 값입니다."
                                          : error.getDefaultMessage()
                        ))
                        .toList();

        return ResponseEntity.badRequest()
                .body(DeliveryErrorResponse.of(DeliveryErrorCode.VALIDATION_FAILED, errors));
    }

    /**
     * 파싱할 수 없거나 필수 본문이 없는 요청을 내부 파싱 정보 없이 반환한다.
     *
     * @param exception 요청 본문 해석 오류
     * @return 공통 형식의 HTTP 400 응답
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<DeliveryErrorResponse> handleUnreadableMessageException(
            HttpMessageNotReadableException exception
    ) {
        return ResponseEntity.badRequest()
                .body(DeliveryErrorResponse.from(DeliveryErrorCode.INVALID_REQUEST_BODY));
    }

    /**
     * URL 또는 쿼리 파라미터의 타입 변환 실패를 반환한다.
     *
     * @param exception 파라미터 타입 변환 오류
     * @return 파라미터 이름을 포함한 HTTP 400 응답
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<DeliveryErrorResponse> handleTypeMismatchException(
            MethodArgumentTypeMismatchException exception
    ) {
        DeliveryErrorResponse.FieldError error =
                new DeliveryErrorResponse.FieldError(exception.getName(), "요청값의 형식이 올바르지 않습니다.");

        return ResponseEntity.badRequest()
                .body(DeliveryErrorResponse.of(DeliveryErrorCode.INVALID_PARAMETER, List.of(error)));
    }

    /**
     * Spring의 HTTP 상태와 응답 헤더를 보존하고 예상하지 못한 오류는 500으로 처리한다.
     * 내부 예외 메시지, SQL 및 외부 응답 본문은 클라이언트에 노출하지 않는다.
     *
     * @param exception 별도 처리기에 해당하지 않는 예외
     * @return 공통 오류 응답
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<DeliveryErrorResponse> handleException(Exception exception) {
        if (exception instanceof ErrorResponse errorResponse) {
            int status = errorResponse.getStatusCode().value();
            if (status >= 500) {
                log.error("HTTP 요청 처리 실패: status={}", status, exception);
            }
            DeliveryErrorCode code = switch (status) {
                case 400 -> DeliveryErrorCode.INVALID_PARAMETER;
                case 404 -> DeliveryErrorCode.ENDPOINT_NOT_FOUND;
                case 405 -> DeliveryErrorCode.METHOD_NOT_ALLOWED;
                case 406 -> DeliveryErrorCode.NOT_ACCEPTABLE;
                case 415 -> DeliveryErrorCode.UNSUPPORTED_MEDIA_TYPE;
                default -> status >= 500
                        ? DeliveryErrorCode.INTERNAL_SERVER_ERROR
                        : DeliveryErrorCode.HTTP_REQUEST_FAILED;
            };
            return ResponseEntity.status(errorResponse.getStatusCode())
                    .headers(errorResponse.getHeaders())
                    .body(DeliveryErrorResponse.from(code));
        }

        log.error("예상하지 못한 배송 처리 실패", exception);
        return ResponseEntity.internalServerError()
                .body(DeliveryErrorResponse.from(DeliveryErrorCode.INTERNAL_SERVER_ERROR));
    }
}

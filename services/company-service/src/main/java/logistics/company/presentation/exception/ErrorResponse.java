package logistics.company.presentation.exception;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ErrorResponse { // 에러 응답 DTO

    private LocalDateTime timestamp;            // 에러 발생 시각
    private int status;                         // HTTP 상태 코드
    private String code;                        // ErrorCode 이름
    private String message;                     // 에러 메시지
    private String path;                        // 요청 경로
    private List<FieldError> errors;            // 필드별 검증 오류 (검증 실패 시에만 값 있음)

    @Getter
    @AllArgsConstructor
    public static class FieldError {
        private String field;                   // 오류 필드명
        private String message;                 // 오류 메시지
    }

    public static ErrorResponse of(ErrorCode errorCode, String path) {
        return of(errorCode, errorCode.getMessage(), path, List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, String path, List<FieldError> errors) {
        return of(errorCode, errorCode.getMessage(), path, errors);
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, String path, List<FieldError> errors) {
        return new ErrorResponse(
                LocalDateTime.now(),
                errorCode.getStatusCode(),
                errorCode.name(),
                message,
                path,
                errors
        );
    }
}
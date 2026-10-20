package logistics.user.presentation.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    MISSING_USER_HEADER(HttpStatus.UNAUTHORIZED, "인증 정보가 없습니다."),
    INVALID_USER_HEADER(HttpStatus.UNAUTHORIZED, "인증 정보가 올바르지 않습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다."),
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "이미 존재하는 아이디입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 존재하는 이메일입니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    DUPLICATE_SLACK_ID(HttpStatus.CONFLICT, "이미 존재하는 슬랙ID입니다."),
    FORBIDDEN_NOT_OWNER(HttpStatus.FORBIDDEN, "본인의 정보만 조회할 수 있습니다."),

    // 허브명으로 검색 실패 시
    HUB_NOT_FOUND(HttpStatus.BAD_REQUEST, "존재하지 않는 허브입니다."),
    // 허브 서버 꺼져있을 경우
    HUB_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "허브 정보를 확인할 수 없습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String message;
}
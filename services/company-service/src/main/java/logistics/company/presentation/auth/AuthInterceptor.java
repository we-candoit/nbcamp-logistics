package logistics.company.presentation.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import logistics.company.presentation.exception.BusinessException;
import logistics.company.presentation.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    // Gateway가 보내는 헤더 이름 (Gateway 확정 후 수정)
    public static final String LOGIN_ID_HEADER = "X-User-Id";
    public static final String ROLE_HEADER = "X-Role-Name";

    // Controller 진입 전: 헤더가 올바르면 Holder에 저장
    // 헤더가 없거나 형식이 틀리면 저장하지 않음 → 로그인이 필요한 곳에서 getRequired()가 401 처리
    // Controller 진입 전 헤더 확인 후 request attribute에 저장
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userId = checkHeader(request, LOGIN_ID_HEADER);
        checkHeader(request, ROLE_HEADER);
        validateUuid(userId);
        return true;
    }

    // 헤더가 없거나 공백이면 401, 있으면 같은 이름으로 request attribute에 저장
    private String checkHeader(HttpServletRequest request, String header) {
        String value = request.getHeader(header);
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        request.setAttribute(header, value);
        return value;
    }

    // 사용자 ID가 UUID 형식이 아니면 401
    private void validateUuid(String value) {
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}
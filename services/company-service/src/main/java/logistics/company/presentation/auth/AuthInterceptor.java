package logistics.company.presentation.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import logistics.company.presentation.exception.BusinessException;
import logistics.company.presentation.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    // Gateway가 보내는 헤더 이름 (Gateway 확정 후 수정)
    public static final String LOGIN_ID_HEADER = "X-User-Login-Id";
    public static final String ROLE_HEADER = "X-User-Role";

    // Controller 진입 전: 헤더가 올바르면 Holder에 저장
    // 헤더가 없거나 형식이 틀리면 저장하지 않음 → 로그인이 필요한 곳에서 getRequired()가 401 처리
    // Controller 진입 전 헤더 확인 후 request attribute에 저장
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        checkHeader(request, LOGIN_ID_HEADER);
        checkHeader(request, ROLE_HEADER);
        return true;
    }

    // 헤더가 없거나 공백이면 401, 있으면 같은 이름으로 저장
    private void checkHeader(HttpServletRequest request, String header) {
        String value = request.getHeader(header);
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        request.setAttribute(header, value);
    }
}
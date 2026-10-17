package logistics.delivery.presentation.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    public static final String USER_HEADER = "X-User-Name";
    public static final String ROLE_HEADER = "X-Role-Name";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String username = request.getHeader(USER_HEADER);
        String role = request.getHeader(ROLE_HEADER);

        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "사용자 이름 헤더가 필요합니다.");
        }

        if (role == null || role.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "역할 헤더가 필요합니다."
            );
        }

        request.setAttribute(USER_HEADER, username);
        request.setAttribute(ROLE_HEADER, role);

        return true;
    }
}

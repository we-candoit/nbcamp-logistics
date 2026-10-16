package logistics.order.presentation;

import ch.qos.logback.core.spi.ErrorCodes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import logistics.order.presentation.exception.CustomException;
import logistics.order.presentation.exception.ErrorCode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String USER_HEADER = "X-User-Id";
    public static final String ROLE_HEADER = "X-Role-Name"; //변경필요

    //컨트롤러 진입 전에 X-User-Id 헤더 존재 여부를 검증
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        //X-User-Id 헤더확인
        String userId = request.getHeader(USER_HEADER);
        if (userId == null) {
            throw new CustomException(ErrorCode.C401_MISSING_USER_HEADER);
        }

        //X-Role-Name 헤더확인
        String role = request.getHeader(ROLE_HEADER);
        if (role == null) {
            throw new CustomException(ErrorCode.C401_MISSING_ROLE_HEADER);
        }

        //전역으로 등록
        request.setAttribute(USER_HEADER, userId);
        request.setAttribute(ROLE_HEADER, role);

        return true;
    }
}

package logistics.delivery.presentation.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String ROLE_HEADER = "X-Role-Name";

    public static UUID getUserId(HttpServletRequest request){
        String userId = request.getHeader(USER_ID_HEADER);

        if(userId == null || userId.isBlank()){
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "사용자 ID 헤더가 필요합니다."
            );
        }

        try{
            UUID parsed = UUID.fromString(userId);
            if (!parsed.toString().equalsIgnoreCase(userId)) {
                throw new IllegalArgumentException();
            }
            return parsed;
        } catch(IllegalArgumentException e){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "사용자 ID는 UUID 형식이어야 합니다."
            );
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UUID userId = getUserId(request);
        String role = request.getHeader(ROLE_HEADER);

        if (role == null || role.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "역할 헤더가 필요합니다."
            );
        }

        request.setAttribute(USER_ID_HEADER, userId);
        request.setAttribute(ROLE_HEADER, role);

        return true;
    }
}

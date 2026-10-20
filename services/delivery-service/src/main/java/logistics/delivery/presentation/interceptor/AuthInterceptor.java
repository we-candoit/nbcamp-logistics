package logistics.delivery.presentation.interceptor;

import logistics.delivery.common.exception.DeliveryErrorCode;
import logistics.delivery.common.exception.DeliveryException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String ROLE_HEADER = "X-Role-Name";

    /**
     * 사용자 ID 헤더를 필수·UUID 형식으로 검증한다.
     * @param request 사용자 정보를 전달한 요청
     * @return 요청자의 사용자 UUID
     * @throws DeliveryException 사용자 ID가 없거나 UUID 형식이 아닌 경우
     */
    public static UUID getUserId(HttpServletRequest request){
        String userId = request.getHeader(USER_ID_HEADER);

        if(userId == null || userId.isBlank()){
            throw new DeliveryException(DeliveryErrorCode.USER_ID_REQUIRED);
        }

        try{
            UUID parsed = UUID.fromString(userId);
            if (!parsed.toString().equalsIgnoreCase(userId)) {
                throw new IllegalArgumentException();
            }
            return parsed;
        } catch(IllegalArgumentException e){
            throw new DeliveryException(DeliveryErrorCode.INVALID_USER_ID, e);
        }
    }

    /**
     * 사용자 ID와 역할을 검증하고 감사 기록 및 외부 API 전달용 요청 속성에 보관한다.
     * @param request 처리할 요청
     * @param response HTTP 응답
     * @param handler 실행할 핸들러
     * @return 필수 헤더가 확인되면 true
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UUID userId = getUserId(request);
        String role = request.getHeader(ROLE_HEADER);

        if (role == null || role.isBlank()) {
            throw new DeliveryException(DeliveryErrorCode.ROLE_REQUIRED);
        }

        request.setAttribute(USER_ID_HEADER, userId);
        request.setAttribute(ROLE_HEADER, role);

        return true;
    }
}

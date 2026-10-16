package logistics.order.common;

import logistics.order.presentation.AuthInterceptor;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.RequestScope;

import java.util.Optional;
import java.util.UUID;

import static org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST;

public class CustomUtils {

    public static Optional<UUID> getUserId() {
        //등록된 username 가져오기
        Object username = RequestContextHolder
                .currentRequestAttributes()
                .getAttribute(AuthInterceptor.USER_HEADER, RequestAttributes.SCOPE_REQUEST);

        //UUID인 경우 캐스팅
        if (username instanceof UUID) {
            return Optional.of(UUID.fromString(username.toString()));
        }

        return Optional.empty();
    }
}

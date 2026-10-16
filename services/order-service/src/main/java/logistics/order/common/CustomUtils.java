package logistics.order.common;

import org.springframework.web.context.request.RequestContextHolder;

import java.util.Optional;
import java.util.UUID;

import static logistics.order.presentation.AuthInterceptor.*;
import static org.springframework.web.context.request.RequestAttributes.*;

public class CustomUtils {

    public static Optional<UUID> getUserId() {
        //등록된 username 가져오기
        Object userId = RequestContextHolder
                .currentRequestAttributes()
                .getAttribute(USER_HEADER, SCOPE_REQUEST);

        //UUID인 경우 캐스팅
        if (userId instanceof UUID) {
            return Optional.of((UUID) userId);
        }

        return Optional.empty();
    }
}

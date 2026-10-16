package logistics.order.common;

import org.springframework.web.context.request.RequestContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.springframework.web.context.request.RequestAttributes.*;

public class GlobalUtils {

    public static Optional<UUID> getUserId() {
        //등록된 username 가져오기
        Object userId = RequestContextHolder
                .currentRequestAttributes()
                .getAttribute(AuthHeaders.USER_ID, SCOPE_REQUEST);

        //UUID인 경우 캐스팅
        if (userId instanceof UUID) {
            return Optional.of((UUID) userId);
        }

        return Optional.empty();
    }

    public static Optional<String> getUserRole() {
        //등록된 username 가져오기
        Object role = RequestContextHolder
                .currentRequestAttributes()
                .getAttribute(AuthHeaders.ROLE, SCOPE_REQUEST);

        //UUID인 경우 캐스팅
        if (role instanceof String) {
            return Optional.of(role.toString());
        }

        return Optional.empty();
    }

}

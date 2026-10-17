package logistics.order.common;

import logistics.order.presentation.exception.CustomException;
import logistics.order.presentation.exception.ErrorCode;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.springframework.web.context.request.RequestAttributes.*;

public class AuthContext {

    public static Optional<UUID> findUserId() {
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

    public static UUID getUserId() {
        return findUserId().orElseThrow(
                () -> new CustomException(ErrorCode.C500_INTERNAL_SERVER_ERROR)
        );
    }

    public static Optional<String> findUserRole() {
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

    public static String getUserRole() {
        return findUserRole().orElseThrow(
                () -> new CustomException(ErrorCode.C500_INTERNAL_SERVER_ERROR)
        );
    }


}

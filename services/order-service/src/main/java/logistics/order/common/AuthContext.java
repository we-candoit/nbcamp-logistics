package logistics.order.common;

import logistics.order.common.exception.CustomException;
import logistics.order.common.exception.ErrorCode;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.springframework.web.context.request.RequestAttributes.*;

public class AuthContext {

    //전체 권한 역할명 (대소문자 구분)
    public static final String MASTER_ROLE = "MASTER";

    //요청 없이 실행되는 작업(스케줄러 등)의 사용자 / 외부 호출 헤더, 감사 정보(createdBy 등)에 사용
    private static final ThreadLocal<SystemUser> SYSTEM_USER = new ThreadLocal<>();

    private record SystemUser(UUID userId, String role) {
    }

    //작업을 시스템 사용자로 실행 / 실행 중에는 요청 정보보다 시스템 사용자를 우선, 끝나면 비움
    public static void runAsSystem(UUID userId, String role, Runnable task) {
        SYSTEM_USER.set(new SystemUser(userId, role));
        try {
            task.run();
        } finally {
            SYSTEM_USER.remove();
        }
    }

    public static Optional<UUID> findUserId() {
        SystemUser systemUser = SYSTEM_USER.get();
        if (systemUser != null) {
            return Optional.of(systemUser.userId());
        }


        //등록된 username 가져오기
        Object userId = RequestContextHolder
                .currentRequestAttributes()
                .getAttribute(AuthHeaders.USER_ID, SCOPE_REQUEST);

        //String인 경우 UUID로 변환 / UUID 형식이 아니면 empty
        if (userId instanceof String stringUserId) {
            try {
                return Optional.of(UUID.fromString(stringUserId));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    public static UUID getUserId() {
        return findUserId().orElseThrow(
                () -> new CustomException(ErrorCode.C500_INTERNAL_SERVER_ERROR)
        );
    }

    public static Optional<String> findUserRole() {
        SystemUser systemUser = SYSTEM_USER.get();
        if (systemUser != null) {
            return Optional.of(systemUser.role());
        }

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

    //현재 유저가 MASTER인가? / role이 없으면 getUserRole()과 동일하게 예외
    public static boolean isMaster() {
        return MASTER_ROLE.equals(getUserRole());
    }


}

package logistics.order.infrastructure;

import logistics.order.common.AuthContext;
import logistics.order.common.AuthHeaders;
import logistics.order.presentation.AuthInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuditorAwareImplTest {

    private final AuditorAwareImpl auditorAwareImpl = new AuditorAwareImpl();
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @BeforeEach
    void beforeEach() {
        // 실제 서버에서 DispatcherServlet이 하는 일을 테스트에서 직접 수행
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void afterEach() {
        // ThreadLocal이라 비우지 않으면 다음 테스트에 남음
        RequestContextHolder.resetRequestAttributes();
    }

    //요청 없이 시스템 사용자로 실행 중인 경우 (스케줄러 등)
    @Test
    public void 시스템_사용자로_실행_중이면_시스템_사용자가_감사_정보가_된다() {
        //given
        RequestContextHolder.resetRequestAttributes();
        UUID systemUserId = UUID.randomUUID();
        Object[] auditor = new Object[1];

        //when
        AuthContext.runAsSystem(systemUserId, "MASTER", () -> auditor[0] = auditorAwareImpl.getCurrentAuditor());

        //then
        assertThat(auditor[0]).isEqualTo(Optional.of(systemUserId));
    }

    //username이 있는 경우
    @Test
    public void username이_있는_경우() {
        //given
        UUID userId = UUID.randomUUID();
        request.setAttribute(AuthHeaders.USER_ID, userId.toString());

        //when
        Optional<UUID> findUserId = auditorAwareImpl.getCurrentAuditor();

        //then
        assertThat(findUserId).contains(userId);
    }

    //username이 UUID 형식이 아닌 경우
    @Test
    public void username이_UUID형식이_아닌_경우() {
        //given
        request.setAttribute(AuthHeaders.USER_ID, "user1");

        //when
        Optional<UUID> findUserId = auditorAwareImpl.getCurrentAuditor();

        //then
        assertThat(findUserId).isEmpty();
    }

    //username이 null인 경우
    @Test
    public void username이_없는_경우() {
        //given

        //when
        Optional<UUID> findUserId = auditorAwareImpl.getCurrentAuditor();

        //then
        assertThat(findUserId).isEmpty();
    }
}

package logistics.order.infrastructure;

import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

import static logistics.order.common.AuthHeaders.ROLE;
import static logistics.order.common.AuthHeaders.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeignRequestInterceptorTest {

    private final FeignRequestInterceptor interceptor = new FeignRequestInterceptor();
    private RequestTemplate template;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        template = new RequestTemplate();
        request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Nested
    @DisplayName("정상 케이스")
    class Success {

        @Test
        @DisplayName("요청 속성에 userId, role이 있으면 두 헤더 모두 추가된다")
        void apply_withUserIdAndRole() {
            UUID userId = UUID.randomUUID();
            request.setAttribute(USER_ID, userId.toString());
            request.setAttribute(ROLE, "MASTER");

            interceptor.apply(template);

            assertThat(template.headers().get(USER_ID)).containsExactly(userId.toString());
            assertThat(template.headers().get(ROLE)).containsExactly("MASTER");
        }

        @Test
        @DisplayName("userId만 있으면 USER_ID 헤더만 추가된다")
        void apply_withOnlyUserId() {
            UUID userId = UUID.randomUUID();
            request.setAttribute(USER_ID, userId.toString());

            interceptor.apply(template);

            assertThat(template.headers().get(USER_ID)).containsExactly(userId.toString());
            assertThat(template.headers()).doesNotContainKey(ROLE);
        }

        @Test
        @DisplayName("role만 있으면 ROLE 헤더만 추가된다")
        void apply_withOnlyRole() {
            request.setAttribute(ROLE, "MASTER");

            interceptor.apply(template);

            assertThat(template.headers()).doesNotContainKey(USER_ID);
            assertThat(template.headers().get(ROLE)).containsExactly("MASTER");
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class Failure {

        @Test
        @DisplayName("요청 속성이 비어있으면 헤더를 추가하지 않는다")
        void apply_withoutAttributes() {
            interceptor.apply(template);

            assertThat(template.headers()).doesNotContainKeys(USER_ID, ROLE);
        }

        @Test
        @DisplayName("userId가 UUID 형식이 아닌 문자열이면 예외 없이 USER_ID 헤더를 추가하지 않는다")
        void apply_withInvalidUserIdFormat() {
            request.setAttribute(USER_ID, "user1");
            request.setAttribute(ROLE, "MASTER");

            interceptor.apply(template);

            assertThat(template.headers()).doesNotContainKey(USER_ID);
            assertThat(template.headers().get(ROLE)).containsExactly("MASTER");
        }

        @Test
        @DisplayName("userId가 String 타입이 아니면 USER_ID 헤더를 추가하지 않는다")
        void apply_withInvalidUserIdType() {
            request.setAttribute(USER_ID, UUID.randomUUID());
            request.setAttribute(ROLE, "MASTER");

            interceptor.apply(template);

            assertThat(template.headers()).doesNotContainKey(USER_ID);
            assertThat(template.headers().get(ROLE)).containsExactly("MASTER");
        }

        @Test
        @DisplayName("role이 String 타입이 아니면 ROLE 헤더를 추가하지 않는다")
        void apply_withInvalidRoleType() {
            UUID userId = UUID.randomUUID();
            request.setAttribute(USER_ID, userId.toString());
            request.setAttribute(ROLE, 1);

            interceptor.apply(template);

            assertThat(template.headers().get(USER_ID)).containsExactly(userId.toString());
            assertThat(template.headers()).doesNotContainKey(ROLE);
        }

        @Test
        @DisplayName("요청 컨텍스트가 없는 스레드(비동기/스케줄러 등)에서 호출하면 IllegalStateException이 발생한다")
        void apply_withoutRequestContext() {
            RequestContextHolder.resetRequestAttributes();

            assertThatThrownBy(() -> interceptor.apply(template))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(template.headers()).isEmpty();
        }
    }
}

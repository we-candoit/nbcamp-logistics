package logistics.order.common;

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

class GlobalUtilsTest {

    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Nested
    @DisplayName("getUserId()")
    class GetUserId {

        @Nested
        @DisplayName("정상 실행")
        class Success {

            @Test
            @DisplayName("요청 속성에 UUID 타입 userId가 있으면 해당 값을 반환한다")
            void returnsUserId() {
                UUID userId = UUID.randomUUID();
                request.setAttribute(USER_ID, userId);

                assertThat(GlobalUtils.getUserId()).contains(userId);
            }
        }

        @Nested
        @DisplayName("에러 / 예외 상황")
        class Failure {

            @Test
            @DisplayName("요청 속성에 userId가 없으면 Optional.empty()를 반환한다")
            void returnsEmpty_whenAttributeMissing() {
                assertThat(GlobalUtils.getUserId()).isEmpty();
            }

            @Test
            @DisplayName("userId가 UUID가 아닌 다른 타입이면 Optional.empty()를 반환한다")
            void returnsEmpty_whenUserIdIsOtherType() {
                request.setAttribute(USER_ID, 1L);

                assertThat(GlobalUtils.getUserId()).isEmpty();
            }

            @Test
            @DisplayName("요청 컨텍스트가 없으면 IllegalStateException이 발생한다")
            void throws_whenNoRequestContext() {
                RequestContextHolder.resetRequestAttributes();

                assertThatThrownBy(GlobalUtils::getUserId)
                        .isInstanceOf(IllegalStateException.class);
            }
        }
    }

    @Nested
    @DisplayName("getUserRole()")
    class GetUserRole {

        @Nested
        @DisplayName("정상 실행")
        class Success {

            @Test
            @DisplayName("요청 속성에 String 타입 role이 있으면 해당 값을 반환한다")
            void returnsRole() {
                request.setAttribute(ROLE, "MASTER");

                assertThat(GlobalUtils.getUserRole()).contains("MASTER");
            }

            @Test
            @DisplayName("role이 빈 문자열이어도 검증 없이 그대로 반환한다")
            void returnsBlank_whenRoleIsBlank() {
                request.setAttribute(ROLE, "");

                assertThat(GlobalUtils.getUserRole()).contains("");
            }

        }
        @Nested
        @DisplayName("에러 / 예외 상황")
        class Failure {


            @Test
            @DisplayName("요청 속성에 role이 없으면 Optional.empty()를 반환한다")
            void returnsEmpty_whenAttributeMissing() {
                assertThat(GlobalUtils.getUserRole()).isEmpty();
            }

            @Test
            @DisplayName("role이 String이 아닌 타입이면 Optional.empty()를 반환한다")
            void returnsEmpty_whenRoleIsNotString() {
                request.setAttribute(ROLE, 1);

                assertThat(GlobalUtils.getUserRole()).isEmpty();
            }

            @Test
            @DisplayName("요청 컨텍스트가 없으면 IllegalStateException이 발생한다")
            void throws_whenNoRequestContext() {
                RequestContextHolder.resetRequestAttributes();

                assertThatThrownBy(GlobalUtils::getUserRole)
                        .isInstanceOf(IllegalStateException.class);
            }
        }
    }
}

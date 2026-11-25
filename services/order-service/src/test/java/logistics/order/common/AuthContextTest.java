package logistics.order.common;

import logistics.order.common.exception.CustomException;
import logistics.order.common.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthContextTest {

    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        //실제 서버에서 DispatcherServlet이 하는 일을 테스트에서 직접 수행
        request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        //ThreadLocal이라 비우지 않으면 다음 테스트에 남음
        RequestContextHolder.resetRequestAttributes();
    }

    private static void assertErrorCode(Throwable e, ErrorCode errorCode) {
        assertThat(e).isInstanceOf(CustomException.class);
        assertThat(((CustomException) e).getErrorCode()).isEqualTo(errorCode);
    }

    @Nested
    @DisplayName("runAsSystem()")
    class RunAsSystem {

        private final UUID systemUserId = UUID.randomUUID();

        @Test
        public void 요청_컨텍스트가_없어도_실행_중에는_시스템_사용자를_반환한다() {
            //given
            RequestContextHolder.resetRequestAttributes();
            UUID[] userId = new UUID[1];
            String[] role = new String[1];

            //when
            AuthContext.runAsSystem(systemUserId, "MASTER", () -> {
                userId[0] = AuthContext.getUserId();
                role[0] = AuthContext.getUserRole();
            });

            //then
            assertThat(userId[0]).isEqualTo(systemUserId);
            assertThat(role[0]).isEqualTo("MASTER");
        }

        @Test
        public void 요청_정보가_있어도_실행_중에는_시스템_사용자가_우선한다() {
            //given
            request.setAttribute(AuthHeaders.USER_ID, UUID.randomUUID().toString());
            request.setAttribute(AuthHeaders.ROLE, "COMPANY_MANAGER");
            UUID[] userId = new UUID[1];

            //when
            AuthContext.runAsSystem(systemUserId, "MASTER", () -> userId[0] = AuthContext.getUserId());

            //then
            assertThat(userId[0]).isEqualTo(systemUserId);
        }

        @Test
        public void 실행이_끝나면_시스템_사용자를_비운다() {
            //given
            RequestContextHolder.resetRequestAttributes();

            //when
            AuthContext.runAsSystem(systemUserId, "MASTER", () -> {
            });

            //then
            assertThatThrownBy(AuthContext::findUserId).isInstanceOf(IllegalStateException.class);
        }

        @Test
        public void 작업이_예외로_끝나도_시스템_사용자를_비운다() {
            //given
            RequestContextHolder.resetRequestAttributes();

            //when
            assertThatThrownBy(() -> AuthContext.runAsSystem(systemUserId, "MASTER", () -> {
                throw new RuntimeException("작업 실패");
            })).hasMessage("작업 실패");

            //then
            assertThatThrownBy(AuthContext::findUserId).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("findUserId()")
    class FindUserId {

        @Nested
        @DisplayName("정상 케이스")
        class Success {

            @Test
            public void UUID형식_문자열이면_UUID로_변환해서_반환한다() {
                //given
                UUID userId = UUID.randomUUID();
                request.setAttribute(AuthHeaders.USER_ID, userId.toString());

                //when, then
                assertThat(AuthContext.findUserId()).contains(userId);
            }

            @Test
            public void 대문자_UUID_문자열도_같은_UUID로_변환한다() {
                //given
                UUID userId = UUID.randomUUID();
                request.setAttribute(AuthHeaders.USER_ID, userId.toString().toUpperCase());

                //when, then
                assertThat(AuthContext.findUserId()).contains(userId);
            }
        }

        @Nested
        @DisplayName("실패 케이스")
        class Failure {

            @Test
            public void 값이_없으면_empty를_반환한다() {
                //when, then
                assertThat(AuthContext.findUserId()).isEmpty();
            }

            @Test
            public void UUID형식이_아닌_문자열이면_예외없이_empty를_반환한다() {
                //given
                request.setAttribute(AuthHeaders.USER_ID, "user1");

                //when, then
                assertThat(AuthContext.findUserId()).isEmpty();
            }

            @Test
            public void 빈_문자열이면_empty를_반환한다() {
                //given
                request.setAttribute(AuthHeaders.USER_ID, "");

                //when, then
                assertThat(AuthContext.findUserId()).isEmpty();
            }

            @Test
            public void String이_아닌_타입이면_empty를_반환한다() {
                //given
                request.setAttribute(AuthHeaders.USER_ID, UUID.randomUUID());

                //when, then
                assertThat(AuthContext.findUserId()).isEmpty();
            }

            @Test
            public void 요청_컨텍스트가_없으면_IllegalStateException이_발생한다() {
                //given
                RequestContextHolder.resetRequestAttributes();

                //when, then
                assertThatThrownBy(AuthContext::findUserId)
                        .isInstanceOf(IllegalStateException.class);
            }
        }
    }

    @Nested
    @DisplayName("getUserId()")
    class GetUserId {

        @Nested
        @DisplayName("정상 케이스")
        class Success {

            @Test
            public void UUID형식_문자열이면_UUID를_반환한다() {
                //given
                UUID userId = UUID.randomUUID();
                request.setAttribute(AuthHeaders.USER_ID, userId.toString());

                //when, then
                assertThat(AuthContext.getUserId()).isEqualTo(userId);
            }
        }

        @Nested
        @DisplayName("실패 케이스")
        class Failure {

            @Test
            public void 값이_없으면_C500_예외가_발생한다() {
                //when, then
                assertThatThrownBy(AuthContext::getUserId)
                        .satisfies(e -> assertErrorCode(e, ErrorCode.C500_INTERNAL_SERVER_ERROR));
            }

            @Test
            public void UUID형식이_아닌_문자열이면_C500_예외가_발생한다() {
                //given
                request.setAttribute(AuthHeaders.USER_ID, "user1");

                //when, then
                assertThatThrownBy(AuthContext::getUserId)
                        .satisfies(e -> assertErrorCode(e, ErrorCode.C500_INTERNAL_SERVER_ERROR));
            }

            @Test
            public void 요청_컨텍스트가_없으면_IllegalStateException이_발생한다() {
                //given
                RequestContextHolder.resetRequestAttributes();

                //when, then
                assertThatThrownBy(AuthContext::getUserId)
                        .isInstanceOf(IllegalStateException.class);
            }
        }
    }

    @Nested
    @DisplayName("findUserRole()")
    class FindUserRole {

        @Nested
        @DisplayName("정상 케이스")
        class Success {

            @Test
            public void 문자열_role이면_그대로_반환한다() {
                //given
                request.setAttribute(AuthHeaders.ROLE, "MASTER");

                //when, then
                assertThat(AuthContext.findUserRole()).contains("MASTER");
            }

            @Test
            public void 대소문자를_변환하지_않고_그대로_반환한다() {
                //given
                request.setAttribute(AuthHeaders.ROLE, "master");

                //when, then
                assertThat(AuthContext.findUserRole()).contains("master");
            }
        }

        @Nested
        @DisplayName("실패 케이스")
        class Failure {

            @Test
            public void 값이_없으면_empty를_반환한다() {
                //when, then
                assertThat(AuthContext.findUserRole()).isEmpty();
            }

            @Test
            public void String이_아닌_타입이면_empty를_반환한다() {
                //given
                request.setAttribute(AuthHeaders.ROLE, 1);

                //when, then
                assertThat(AuthContext.findUserRole()).isEmpty();
            }

            @Test
            public void 요청_컨텍스트가_없으면_IllegalStateException이_발생한다() {
                //given
                RequestContextHolder.resetRequestAttributes();

                //when, then
                assertThatThrownBy(AuthContext::findUserRole)
                        .isInstanceOf(IllegalStateException.class);
            }
        }
    }

    @Nested
    @DisplayName("getUserRole()")
    class GetUserRole {

        @Nested
        @DisplayName("정상 케이스")
        class Success {

            @Test
            public void 문자열_role이면_그대로_반환한다() {
                //given
                request.setAttribute(AuthHeaders.ROLE, "MASTER");

                //when, then
                assertThat(AuthContext.getUserRole()).isEqualTo("MASTER");
            }
        }

        @Nested
        @DisplayName("실패 케이스")
        class Failure {

            @Test
            public void 값이_없으면_C500_예외가_발생한다() {
                //when, then
                assertThatThrownBy(AuthContext::getUserRole)
                        .satisfies(e -> assertErrorCode(e, ErrorCode.C500_INTERNAL_SERVER_ERROR));
            }

            @Test
            public void String이_아닌_타입이면_C500_예외가_발생한다() {
                //given
                request.setAttribute(AuthHeaders.ROLE, 1);

                //when, then
                assertThatThrownBy(AuthContext::getUserRole)
                        .satisfies(e -> assertErrorCode(e, ErrorCode.C500_INTERNAL_SERVER_ERROR));
            }

            @Test
            public void 요청_컨텍스트가_없으면_IllegalStateException이_발생한다() {
                //given
                RequestContextHolder.resetRequestAttributes();

                //when, then
                assertThatThrownBy(AuthContext::getUserRole)
                        .isInstanceOf(IllegalStateException.class);
            }
        }
    }

    @Nested
    @DisplayName("isMaster()")
    class IsMaster {

        @Nested
        @DisplayName("정상 케이스")
        class Success {

            @Test
            public void role이_MASTER면_true를_반환한다() {
                //given
                request.setAttribute(AuthHeaders.ROLE, AuthContext.MASTER_ROLE);

                //when, then
                assertThat(AuthContext.isMaster()).isTrue();
            }

            @Test
            public void role이_MASTER가_아니면_false를_반환한다() {
                //given
                request.setAttribute(AuthHeaders.ROLE, "COMPANY_MANAGER");

                //when, then
                assertThat(AuthContext.isMaster()).isFalse();
            }

            @Test
            public void 소문자_master는_MASTER로_인정하지_않는다() {
                //given
                request.setAttribute(AuthHeaders.ROLE, "master");

                //when, then
                assertThat(AuthContext.isMaster()).isFalse();
            }
        }

        @Nested
        @DisplayName("실패 케이스")
        class Failure {

            @Test
            public void role이_없으면_C500_예외가_발생한다() {
                //when, then
                assertThatThrownBy(AuthContext::isMaster)
                        .satisfies(e -> assertErrorCode(e, ErrorCode.C500_INTERNAL_SERVER_ERROR));
            }
        }
    }
}

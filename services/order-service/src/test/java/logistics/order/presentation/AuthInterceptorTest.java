package logistics.order.presentation;

import logistics.order.presentation.exception.CustomException;
import logistics.order.presentation.exception.ErrorCode;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;


class AuthInterceptorTest {

    private AuthInterceptor authInterceptor = new AuthInterceptor();
    private MockHttpServletRequest request = new MockHttpServletRequest();
    private MockHttpServletResponse response = new MockHttpServletResponse();
    private Object object = new Object();

    @AfterEach
    void afterEach() {
        request = new MockHttpServletRequest();
    }

    //헤더 모두 있는경우
    @Test
    public void 헤더_모두_있는경우() throws Exception {
        //given
        String username = "user1";
        String role = "master";
        request.addHeader(AuthInterceptor.USER_HEADER, username);
        request.addHeader(AuthInterceptor.ROLE_HEADER, role);

        //when
        boolean result = authInterceptor.preHandle(request, response, object);
        Object findUsername = request.getAttribute(AuthInterceptor.USER_HEADER);
        Object findRole = request.getAttribute(AuthInterceptor.ROLE_HEADER);

        //then
        Assertions.assertThat(findUsername instanceof String).isTrue();
        Assertions.assertThat(findRole instanceof String).isTrue();
        Assertions.assertThat(findUsername.toString()).isEqualTo(username);
        Assertions.assertThat(findRole.toString()).isEqualTo(role);
    }

    //Role헤더만 있는경우
    @Test
    public void Role헤더만_있는경우() throws Exception {
        //given
        String role = "master";
        request.addHeader(AuthInterceptor.ROLE_HEADER, role);

        //when
        try {
            authInterceptor.preHandle(request, response, object);

        } catch (Exception e) {
            Object findUsername = request.getAttribute(AuthInterceptor.USER_HEADER);
            Object findRole = request.getAttribute(AuthInterceptor.ROLE_HEADER);

            //then
            Assertions.assertThat(findUsername).isNull();
            Assertions.assertThat(findRole).isNull();
            Assertions.assertThat(e).isInstanceOf(CustomException.class);
            Assertions.assertThat(((CustomException)e).getErrorCode()).isEqualTo(ErrorCode.C401_MISSING_USER_HEADER);
        }
    }

    //User헤더만 있는경우
    @Test
    public void User헤더만_있는경우() throws Exception {
        //given
        String username = "master";
        request.addHeader(AuthInterceptor.USER_HEADER, username);

        //when
        try {
            authInterceptor.preHandle(request, response, object);

        } catch (Exception e) {
            Object findUsername = request.getAttribute(AuthInterceptor.USER_HEADER);
            Object findRole = request.getAttribute(AuthInterceptor.ROLE_HEADER);

            //then
            Assertions.assertThat(findUsername).isNull();
            Assertions.assertThat(findRole).isNull();
            Assertions.assertThat(e).isInstanceOf(CustomException.class);
            Assertions.assertThat(((CustomException)e).getErrorCode()).isEqualTo(ErrorCode.C401_MISSING_ROLE_HEADER);
        }
    }

    //헤더 모두 없는경우
    @Test
    public void 헤더_모두_없는경우() throws Exception {
        //given

        //when
        try {
            authInterceptor.preHandle(request, response, object);

        } catch (Exception e) {
            Object findUsername = request.getAttribute(AuthInterceptor.USER_HEADER);
            Object findRole = request.getAttribute(AuthInterceptor.ROLE_HEADER);

            //then
            Assertions.assertThat(findUsername).isNull();
            Assertions.assertThat(findRole).isNull();
            Assertions.assertThat(e).isInstanceOf(CustomException.class);
            Assertions.assertThat(((CustomException)e).getErrorCode()).isEqualTo(ErrorCode.C401_MISSING_USER_HEADER);
        }
    }
}
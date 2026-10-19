package logistics.delivery;

import feign.RequestTemplate;
import logistics.delivery.infrastructure.audit.AuditorAwareImpl;
import logistics.delivery.infrastructure.client.RequesterHeaderInterceptor;
import logistics.delivery.presentation.interceptor.AuthInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequesterIdentityTest {
    private final AuthInterceptor auth = new AuthInterceptor();
    private final UUID userId = UUID.randomUUID();

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void twoHeadersAreSufficientAndAuditUsesOriginalUuid() {
        MockHttpServletRequest request = request(userId.toString(), "USER");
        assertThat(auth.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertThat(new AuditorAwareImpl().getCurrentAuditor()).contains(userId);

        RequestTemplate template = new RequestTemplate();
        template.header("X-User-Id", UUID.randomUUID().toString());
        template.header("X-Role-Name", "MASTER");
        new RequesterHeaderInterceptor().apply(template);
        assertThat(template.headers().get("X-User-Id")).containsExactly(userId.toString());
        assertThat(template.headers().get("X-Role-Name")).containsExactly("USER");
        assertThat(template.headers()).doesNotContainKey("X-User-Name");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void missingOrBlankUserIdIsUnauthorized(String value) {
        assertRejected(request(value, "USER"), 401);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void missingOrBlankRoleIsUnauthorized(String value) {
        assertRejected(request(userId.toString(), value), 401);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "1-1-1-1-1"})
    void invalidUuidIsBadRequest(String value) {
        assertRejected(request(value, "USER"), 400);
    }

    @Test
    void absentRequestDoesNotInventAnAuditorOrServiceIdentity() {
        assertThat(new AuditorAwareImpl().getCurrentAuditor()).isEmpty();
        RequestTemplate template = new RequestTemplate();
        new RequesterHeaderInterceptor().apply(template);
        assertThat(template.headers()).isEmpty();
    }

    @Test
    void obsoleteUsernameHeaderCannotChangeAuditIdentity() {
        MockHttpServletRequest request = request(userId.toString(), "USER");
        request.addHeader("X-User-Name", "another-user");
        auth.preHandle(request, new MockHttpServletResponse(), new Object());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertThat(new AuditorAwareImpl().getCurrentAuditor()).contains(userId);
    }

    private MockHttpServletRequest request(String id, String role) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (id != null) request.addHeader("X-User-Id", id);
        if (role != null) request.addHeader("X-Role-Name", role);
        return request;
    }

    private void assertRejected(MockHttpServletRequest request, int status) {
        assertThatThrownBy(() -> auth.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode().value()).isEqualTo(status));
    }
}

package logistics.delivery.infrastructure.audit;

import logistics.delivery.presentation.interceptor.AuthInterceptor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Optional;

@Component
public class AuditorAwareImpl implements AuditorAware<String> {
    @Override
    public Optional<String> getCurrentAuditor() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();

        if (attributes == null) {
            return Optional.empty();
        }

        Object username = attributes.getAttribute(AuthInterceptor.USER_HEADER, RequestAttributes.SCOPE_REQUEST);

        if (username instanceof String value && !value.isBlank()) {
            return Optional.of(value);
        }

        return Optional.empty();
    }
}

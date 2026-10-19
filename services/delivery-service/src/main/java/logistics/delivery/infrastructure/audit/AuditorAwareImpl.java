package logistics.delivery.infrastructure.audit;

import logistics.delivery.presentation.interceptor.AuthInterceptor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Optional;
import java.util.UUID;

@Component
public class AuditorAwareImpl implements AuditorAware<UUID> {
    @Override
    public Optional<UUID> getCurrentAuditor() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();

        if (attributes == null) {
            return Optional.empty();
        }

        Object userId = attributes.getAttribute(AuthInterceptor.USER_ID_HEADER, RequestAttributes.SCOPE_REQUEST);

        if (userId instanceof UUID value) {
            return Optional.of(value);
        }

        return Optional.empty();
    }
}

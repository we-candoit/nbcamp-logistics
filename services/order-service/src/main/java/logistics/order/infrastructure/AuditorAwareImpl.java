package logistics.order.infrastructure;

import logistics.order.common.AuthContext;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class AuditorAwareImpl implements AuditorAware<UUID> {

    @NullMarked
    @Override
    public Optional<UUID> getCurrentAuditor() {
        return AuthContext.findUserId();
    }
}

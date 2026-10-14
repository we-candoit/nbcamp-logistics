package logistics.order.infrastructure;

import logistics.order.presentation.AuthInterceptor;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Optional;

@Component
public class AuditorAwareImpl implements AuditorAware<String> {

    @NullMarked
    @Override
    public Optional<String> getCurrentAuditor() {
        //등록된 username 가져오기
        Object username = RequestContextHolder
                .currentRequestAttributes()
                .getAttribute(AuthInterceptor.USER_HEADER, RequestAttributes.SCOPE_REQUEST);

        //String인 경우 캐스팅
        if (username instanceof String) {
            return Optional.of(username.toString());
        }

        return  Optional.empty();
    }
}

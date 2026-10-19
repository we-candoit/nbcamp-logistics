package logistics.user.config;

import logistics.user.common.constant.UserHeaders;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

@Component
public class HeaderAuditorAware implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes == null) {
            return Optional.empty();
        }
        // 헤더가 없으면 empty -> 엔티티에 미리 넣어둔 값(initAuditor)이 유지됨
        return Optional.ofNullable(attributes.getRequest().getHeader(UserHeaders.USER_ID));
    }
}
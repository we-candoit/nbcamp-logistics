package logistics.company.config;

import logistics.company.presentation.auth.AuthInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Optional;

@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {

    // AuthInterceptor가 저장한 login_id를 감사 필드에 기록
    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();

            if (attributes == null) {            // 웹 요청이 아닌 경우
                return Optional.empty();
            }

            Object loginId = attributes.getAttribute(AuthInterceptor.LOGIN_ID_HEADER, RequestAttributes.SCOPE_REQUEST);

            if (loginId instanceof String value && !value.isBlank()) {
                return Optional.of(value);
            }
            return Optional.empty();
        };
    }
}

package logistics.company.config;

import logistics.company.presentation.auth.AuthInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Optional;
import java.util.UUID;

@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {

    // AuthInterceptor가 저장한 사용자 ID(X-User-Id, UUID)를 감사 필드에 기록
    @Bean
    public AuditorAware<UUID> auditorAware() {
        return () -> {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();

            if (attributes == null) {                       // 웹 요청이 아닌 경우
                return Optional.empty();
            }

            // AuthInterceptor에서 UUID 형식 검증을 마친 X-User-Id
            Object userId = attributes.getAttribute(AuthInterceptor.LOGIN_ID_HEADER, RequestAttributes.SCOPE_REQUEST);
            return userId instanceof String value
                    ? Optional.of(UUID.fromString(value))
                    : Optional.empty();                 // "/internal/**"등 인터셉터를 거치지 않은 요청
        };
    }
}

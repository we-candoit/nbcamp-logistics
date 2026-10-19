package logistics.delivery.infrastructure.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import logistics.delivery.presentation.interceptor.AuthInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.UUID;

/** 서비스 간 호출에서도 요청자의 사용자 ID와 권한을 유지해서 전달 */
@Component
public class RequesterHeaderInterceptor implements RequestInterceptor {
    @Override
    public void apply(RequestTemplate template) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) return;

        Object userId = attributes.getAttribute(AuthInterceptor.USER_ID_HEADER, RequestAttributes.SCOPE_REQUEST);
        Object role = attributes.getAttribute(AuthInterceptor.ROLE_HEADER, RequestAttributes.SCOPE_REQUEST);
        if (userId instanceof UUID id && role instanceof String value && !value.isBlank()) {
            template.removeHeader(AuthInterceptor.USER_ID_HEADER);
            template.removeHeader(AuthInterceptor.ROLE_HEADER);
            template.header(AuthInterceptor.USER_ID_HEADER, id.toString());
            template.header(AuthInterceptor.ROLE_HEADER, value);
        }
    }
}

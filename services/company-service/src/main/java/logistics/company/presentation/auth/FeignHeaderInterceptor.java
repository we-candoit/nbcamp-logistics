package logistics.company.presentation.auth;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.List;

public class FeignHeaderInterceptor implements RequestInterceptor {

    private static final List<String> HEADERS = List.of(
            AuthInterceptor.LOGIN_ID_HEADER,
            AuthInterceptor.ROLE_HEADER
    );

    // request attribute에 저장된 사용자 헤더를 그대로 복사, 없으면 헤더 없이 호출
    @Override
    public void apply(RequestTemplate template) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;
        }

        for (String header : HEADERS) {
            Object value = attributes.getAttribute(header, RequestAttributes.SCOPE_REQUEST);
            if (value instanceof String v) {
                template.header(header, v);
            }
        }
    }
}

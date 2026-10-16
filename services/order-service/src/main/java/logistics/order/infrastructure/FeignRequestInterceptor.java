package logistics.order.infrastructure;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import logistics.order.common.GlobalUtils;
import org.springframework.stereotype.Component;
import java.util.UUID;

import static logistics.order.common.AuthHeaders.*;

@Component
public class FeignRequestInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        GlobalUtils.getUserId()
                .map(UUID::toString)
                .ifPresent(id -> template.header(USER_ID, id));

        GlobalUtils.getUserRole()
                .map(String::valueOf)
                .ifPresent(role -> template.header(ROLE, role));

    }
}

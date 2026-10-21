package logistics.order.config;

import feign.Retryer;
import feign.codec.ErrorDecoder;
import logistics.order.infrastructure.FeignErrorDecoder;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//모든 Feign 클라이언트에 공통 적용
@Configuration
@EnableFeignClients(basePackages = "logistics.order")
public class FeignClientConfig {

    //재시도 간격 0.1초에서 시작해 1.5배씩 증가, 최대 1초
    private static final long RETRY_PERIOD_MS = 100;
    private static final long RETRY_MAX_PERIOD_MS = 1_000;

    //최초 호출 포함 시도 횟수 / 실패시 1회 재시도
    private static final int RETRY_MAX_ATTEMPTS = 2;

    //RetryableException(타임아웃, 연결 실패, 5xx)만 재시도
    @Bean
    public Retryer feignRetryer() {
        return new Retryer.Default(RETRY_PERIOD_MS, RETRY_MAX_PERIOD_MS, RETRY_MAX_ATTEMPTS);
    }

    @Bean
    public ErrorDecoder feignErrorDecoder() {
        return new FeignErrorDecoder();
    }
}

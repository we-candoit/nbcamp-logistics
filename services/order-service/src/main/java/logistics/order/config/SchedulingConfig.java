package logistics.order.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

//주문 재보상 스케줄러 실행
@Configuration
@EnableScheduling
public class SchedulingConfig { }

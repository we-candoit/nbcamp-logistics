package logistics.delivery.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** demo 프로필에서만 사용하는 고정 담당자와 시연용 배송 실적 설정 */
@Component
@Profile("demo")
@ConfigurationProperties(prefix = "delivery.demo")
@Validated
@Getter
@Setter
public class DeliveryDemoProperties {

    @Valid
    @NotNull
    private AutoAssignment autoAssignment = new AutoAssignment();

    @Valid
    @NotNull
    private ActualMetrics actualMetrics = new ActualMetrics();

    @Getter
    @Setter
    public static class AutoAssignment {
        private boolean enabled = false;

        @NotNull
        private Map<@NotNull UUID, @NotNull UUID> hubManagers = new LinkedHashMap<>();
    }

    @Getter
    @Setter
    public static class ActualMetrics {
        private boolean enabled = true;

        @Valid
        @NotNull
        private Metrics hubToHub = new Metrics(new BigDecimal("100.00"), 90);

        @Valid
        @NotNull
        private Metrics hubToCompany = new Metrics(new BigDecimal("5.00"), 15);
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metrics {
        @NotNull
        @DecimalMin("0.0")
        private BigDecimal distance;

        @NotNull
        @Min(0)
        private Integer duration;
    }
}

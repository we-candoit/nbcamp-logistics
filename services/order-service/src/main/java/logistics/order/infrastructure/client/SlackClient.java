package logistics.order.infrastructure.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlackClient {

    @Value("${SLACK_TOKEN}")
    private String slackToken;

    //슬랙 응답이 늦어도 주문 처리가 오래 묶이지 않도록 연결 3초, 응답대기 5초로 제한
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final RestClient restClient = RestClient.builder()
            .baseUrl("https://slack.com/api")
            .requestFactory(requestFactory())
            .build();

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    public void sendDm(String slackUserId, String text) {
        Map<?, ?> res = restClient.post()
                .uri("/chat.postMessage")
                .header("Authorization", "Bearer " + slackToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("channel", slackUserId, "text", text))
                .retrieve()
                .body(Map.class);

        if (res == null || !Boolean.TRUE.equals(res.get("ok"))) {
            log.error("Dm failed");
        } else {
            log.info("Dm success.");
        }
    }
}

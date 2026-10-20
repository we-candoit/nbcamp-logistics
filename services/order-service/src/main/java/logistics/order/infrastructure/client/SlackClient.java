package logistics.order.infrastructure.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlackClient {

    @Value("${SLACK_TOKEN}")
    private String slackToken;

    private final RestClient restClient = RestClient.create("https://slack.com/api");

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

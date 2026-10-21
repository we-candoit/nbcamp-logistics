package logistics.order.infrastructure;

import com.sun.net.httpserver.HttpServer;
import feign.Feign;
import feign.FeignException;
import feign.Request;
import feign.RequestLine;
import feign.RetryableException;
import logistics.order.config.FeignClientConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

//실제 HTTP 서버로 FeignClientConfig의 Retryer + ErrorDecoder 동작 확인
class FeignRetryTest {

    interface TestClient {
        @RequestLine("POST /test")
        String call();
    }

    private final FeignClientConfig config = new FeignClientConfig();
    private final AtomicInteger attempts = new AtomicInteger();

    private HttpServer server;
    private TestClient client;

    //서버가 응답할 상태코드 목록 / 호출 순서대로 사용, 마지막 값은 이후 호출에도 사용
    private int[] statuses;
    private long delayMs;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/test", exchange -> {
            int attempt = attempts.getAndIncrement();
            int status = statuses[Math.min(attempt, statuses.length - 1)];
            sleep(delayMs);

            byte[] body = "body".getBytes();
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        //요청마다 별도 스레드에서 처리 / 단일 스레드면 타임아웃 케이스에서 재시도 요청이 이전 요청 처리가 끝날 때까지 대기
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        client = Feign.builder()
                .retryer(config.feignRetryer())
                .errorDecoder(config.feignErrorDecoder())
                //응답대기 0.2초 / 타임아웃 케이스 확인용
                .options(new Request.Options(1, TimeUnit.SECONDS, 200, TimeUnit.MILLISECONDS, true))
                .target(TestClient.class, "http://localhost:" + server.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void respond(int... statuses) {
        this.statuses = statuses;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Nested
    @DisplayName("재시도 대상")
    class Retry {

        @Test
        public void 서버오류_5xx_응답이면_1회_재시도하고_성공하면_응답을_반환한다() {
            //given
            respond(503, 200);

            //when
            String response = client.call();

            //then
            assertThat(response).isEqualTo("body");
            assertThat(attempts.get()).isEqualTo(2);
        }

        @Test
        public void 재시도까지_5xx면_원래_상태코드를_가진_예외가_발생한다() {
            //given
            respond(500);

            //when, then
            assertThatThrownBy(() -> client.call())
                    .isInstanceOf(RetryableException.class)
                    .satisfies(e -> assertThat(((FeignException) e).status()).isEqualTo(500))
                    .hasCauseInstanceOf(FeignException.InternalServerError.class);
            assertThat(attempts.get()).isEqualTo(2);
        }

        @Test
        public void 응답대기_시간을_넘기면_1회_재시도하고_status_마이너스1_예외가_발생한다() {
            //given
            respond(200);
            delayMs = 500;

            //when, then
            assertThatThrownBy(() -> client.call())
                    .isInstanceOf(RetryableException.class)
                    .satisfies(e -> assertThat(((FeignException) e).status()).isEqualTo(-1));
            assertThat(attempts.get()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("재시도 대상 아님")
    class NoRetry {

        @Test
        public void 상태코드_404_응답이면_재시도하지_않고_NotFound_예외가_발생한다() {
            //given
            respond(404);

            //when, then
            assertThatThrownBy(() -> client.call()).isInstanceOf(FeignException.NotFound.class);
            assertThat(attempts.get()).isEqualTo(1);
        }

        @Test
        public void 상태코드_409_응답이면_재시도하지_않고_Conflict_예외가_발생한다() {
            //given
            respond(409);

            //when, then
            assertThatThrownBy(() -> client.call()).isInstanceOf(FeignException.Conflict.class);
            assertThat(attempts.get()).isEqualTo(1);
        }
    }
}

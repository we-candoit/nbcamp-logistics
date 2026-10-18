package logistics.order.presentation.exception;

import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import logistics.order.presentation.dto.response.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private final Request request = Request.create(
            Request.HttpMethod.GET, "http://company-service/api/items/1",
            Map.of(), null, StandardCharsets.UTF_8, null);

    @Nested
    @DisplayName("handleFeignException()")
    class HandleFeignException {

        @Test
        public void 응답을_받지_못하면_503으로_응답한다() {
            //given
            //타임아웃, 연결 실패 시 Feign이 던지는 예외 / status = -1
            RetryableException e = new RetryableException(
                    -1, "Read timed out", Request.HttpMethod.GET, (Long) null, request);

            //when
            ResponseEntity<ErrorResponse> response = handler.handleFeignException(e);

            //then
            assertThat(response.getStatusCode().value()).isEqualTo(503);
            assertThat(response.getBody().getStatus()).isEqualTo(503);
            assertThat(response.getBody().getMessage()).isEqualTo("외부 서비스에 연결할 수 없습니다.");
        }

        @Test
        public void 외부서비스가_에러로_응답하면_해당_상태코드로_응답한다() {
            //given
            FeignException e = new FeignException.NotFound("Not Found", request, null, null);

            //when
            ResponseEntity<ErrorResponse> response = handler.handleFeignException(e);

            //then
            assertThat(response.getStatusCode().value()).isEqualTo(404);
            assertThat(response.getBody().getStatus()).isEqualTo(404);
        }
    }
}

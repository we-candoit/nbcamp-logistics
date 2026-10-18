package logistics.order.presentation.exception;

import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import logistics.order.presentation.controller.OrderController;
import logistics.order.presentation.dto.request.CreateOrderRequest;
import logistics.order.presentation.dto.response.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private final Request request = Request.create(
            Request.HttpMethod.GET, "http://company-service/api/items/1",
            Map.of(), null, StandardCharsets.UTF_8, null);

    //모든 에러 응답은 status / message / timestamp 형식
    private static void assertErrorResponse(ResponseEntity<ErrorResponse> response, int status) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(status);
        assertThat(response.getBody().getTimestamp()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Nested
    @DisplayName("handleCustomException()")
    class HandleCustomException {

        @Test
        public void ErrorCode의_상태코드로_응답한다() {
            //given
            CustomException e = new CustomException(ErrorCode.C409_ORDER_PRICE_CHANGED);

            //when
            ResponseEntity<ErrorResponse> response = handler.handleCustomException(e);

            //then
            assertErrorResponse(response, 409);
        }

        @Test
        public void 메시지를_지정하지_않으면_상태코드의_기본문구로_응답한다() {
            //given
            CustomException e = new CustomException(ErrorCode.C403_FORBIDDEN_REQUESTER);

            //when
            ResponseEntity<ErrorResponse> response = handler.handleCustomException(e);

            //then
            assertErrorResponse(response, 403);
            assertThat(response.getBody().getMessage()).isEqualTo("Forbidden");
        }

        @Test
        public void 메시지를_지정하면_지정한_메시지로_응답한다() {
            //given
            CustomException e = new CustomException(ErrorCode.C400_BAD_REQUEST, "존재하지 않는 상품입니다.");

            //when
            ResponseEntity<ErrorResponse> response = handler.handleCustomException(e);

            //then
            assertErrorResponse(response, 400);
            assertThat(response.getBody().getMessage()).isEqualTo("존재하지 않는 상품입니다.");
        }
    }

    @Nested
    @DisplayName("handleMethodArgumentNotValidException()")
    class HandleMethodArgumentNotValidException {

        private MethodArgumentNotValidException exceptionWith(FieldError... errors) throws Exception {
            BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "createOrderRequest");
            for (FieldError error : errors) {
                bindingResult.addError(error);
            }
            MethodParameter parameter = new MethodParameter(
                    OrderController.class.getMethod("createOrder", CreateOrderRequest.class), 0);

            return new MethodArgumentNotValidException(parameter, bindingResult);
        }

        @Test
        public void 필드명과_검증메시지로_400을_응답한다() throws Exception {
            //given
            MethodArgumentNotValidException e = exceptionWith(
                    new FieldError("createOrderRequest", "quantity", "상품 수량은 1개 이상이어야 합니다."));

            //when
            ResponseEntity<ErrorResponse> response = handler.handleMethodArgumentNotValidException(e);

            //then
            assertErrorResponse(response, 400);
            assertThat(response.getBody().getMessage()).isEqualTo("quantity: 상품 수량은 1개 이상이어야 합니다.");
        }

        @Test
        public void 여러_필드가_틀리면_쉼표로_이어서_응답한다() throws Exception {
            //given
            MethodArgumentNotValidException e = exceptionWith(
                    new FieldError("createOrderRequest", "quantity", "상품 수량은 1개 이상이어야 합니다."),
                    new FieldError("createOrderRequest", "recipientName", "수령인 이름은 필수 입니다."));

            //when
            ResponseEntity<ErrorResponse> response = handler.handleMethodArgumentNotValidException(e);

            //then
            assertErrorResponse(response, 400);
            assertThat(response.getBody().getMessage())
                    .isEqualTo("quantity: 상품 수량은 1개 이상이어야 합니다., recipientName: 수령인 이름은 필수 입니다.");
        }
    }

    @Nested
    @DisplayName("handleHttpMessageNotReadableException()")
    class HandleHttpMessageNotReadableException {

        @Test
        public void 정해진_문구로_400을_응답한다() {
            //given
            HttpMessageNotReadableException e = new HttpMessageNotReadableException(
                    "JSON parse error: Cannot deserialize value of type `java.util.UUID` from String \"abc\"",
                    new MockHttpInputMessage(new byte[0]));

            //when
            ResponseEntity<ErrorResponse> response = handler.handleHttpMessageNotReadableException(e);

            //then
            assertErrorResponse(response, 400);
            assertThat(response.getBody().getMessage()).isEqualTo("요청 형식이 올바르지 않습니다.");
        }

        @Test
        public void Jackson_상세메시지는_응답에_노출하지_않는다() {
            //given
            HttpMessageNotReadableException e = new HttpMessageNotReadableException(
                    "JSON parse error: Cannot deserialize value of type `java.util.UUID` from String \"abc\"",
                    new MockHttpInputMessage(new byte[0]));

            //when
            ResponseEntity<ErrorResponse> response = handler.handleHttpMessageNotReadableException(e);

            //then
            assertThat(response.getBody().getMessage())
                    .doesNotContain("java.util.UUID")
                    .doesNotContain("JSON parse error");
        }
    }

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
            assertErrorResponse(response, 503);
            assertThat(response.getBody().getMessage()).isEqualTo("외부 서비스에 연결할 수 없습니다.");
        }

        @Test
        public void 응답을_받지_못한_경우_내부_메시지를_노출하지_않는다() {
            //given
            RetryableException e = new RetryableException(
                    -1, "Read timed out executing GET http://company-service/api/items/1",
                    Request.HttpMethod.GET, (Long) null, request);

            //when
            ResponseEntity<ErrorResponse> response = handler.handleFeignException(e);

            //then
            assertThat(response.getBody().getMessage()).doesNotContain("company-service");
        }

        @Test
        public void 외부서비스가_에러로_응답하면_해당_상태코드로_응답한다() {
            //given
            FeignException e = new FeignException.NotFound("Not Found", request, null, null);

            //when
            ResponseEntity<ErrorResponse> response = handler.handleFeignException(e);

            //then
            assertErrorResponse(response, 404);
        }

        @Test
        public void 외부서비스가_500으로_응답하면_500으로_응답한다() {
            //given
            FeignException e = new FeignException.InternalServerError("Internal Server Error", request, null, null);

            //when
            ResponseEntity<ErrorResponse> response = handler.handleFeignException(e);

            //then
            //현재 동작: 외부 상태코드·메시지를 그대로 전달 (보상처리 시 502 등으로 정리 예정)
            assertErrorResponse(response, 500);
        }
    }
}

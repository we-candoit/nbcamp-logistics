package logistics.order.infrastructure;

import feign.FeignException;
import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;

//Feign 에러 응답 변환 / 5xx는 일시적인 서버 오류일 수 있어 재시도 대상으로 변환
//4xx는 다시 요청해도 결과가 같으므로 기본 디코더로 처리 (재시도 안함)
//타임아웃, 연결 실패 등 응답이 없는 경우는 Feign이 RetryableException으로 처리
public class FeignErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new ErrorDecoder.Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        if (response.status() >= 500) {
            //원래 예외를 cause로 남겨 응답 본문 등 실패 정보를 로그에서 확인
            FeignException cause = FeignException.errorStatus(methodKey, response);

            return new RetryableException(
                    response.status(),
                    cause.getMessage(),
                    response.request().httpMethod(),
                    cause,
                    (Long) null,
                    response.request()
            );
        }

        return defaultDecoder.decode(methodKey, response);
    }
}

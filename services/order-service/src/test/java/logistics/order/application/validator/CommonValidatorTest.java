package logistics.order.application.validator;

import logistics.order.common.exception.CustomException;
import logistics.order.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommonValidatorTest {

    private static final ErrorCode ERROR_CODE = ErrorCode.C400_BAD_REQUEST;

    @Nested
    @DisplayName("validateEqual()")
    class ValidateEqual {

        @Nested
        @DisplayName("정상 케이스")
        class Success {

            @Test
            public void 같은_UUID면_예외가_발생하지_않는다() {
                //given
                UUID expected = UUID.randomUUID();
                UUID actual = UUID.fromString(expected.toString());

                //when, then
                assertThatCode(() -> CommonValidator.validateEqual(expected, actual, ERROR_CODE))
                        .doesNotThrowAnyException();
            }

            @Test
            public void 캐시범위를_벗어난_같은_Long이면_예외가_발생하지_않는다() {
                //given
                //Long 캐시(-128~127) 밖의 값은 서로 다른 객체 → == 비교였다면 실패
                Long expected = 10_000L;
                Long actual = Long.valueOf(10_000L);

                //when, then
                assertThatCode(() -> CommonValidator.validateEqual(expected, actual, ERROR_CODE))
                        .doesNotThrowAnyException();
            }

            @Test
            public void 둘다_null이면_예외가_발생하지_않는다() {
                //when, then
                assertThatCode(() -> CommonValidator.validateEqual(null, null, ERROR_CODE))
                        .doesNotThrowAnyException();
            }
        }

        @Nested
        @DisplayName("실패 케이스")
        class Failure {

            @Test
            public void 다른_UUID면_전달한_ErrorCode로_예외가_발생한다() {
                //given
                UUID expected = UUID.randomUUID();
                UUID actual = UUID.randomUUID();

                //when, then
                assertThatThrownBy(() -> CommonValidator.validateEqual(expected, actual, ERROR_CODE))
                        .isInstanceOf(CustomException.class)
                        .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ERROR_CODE));
            }

            @Test
            public void 다른_Long이면_전달한_ErrorCode로_예외가_발생한다() {
                //given
                ErrorCode errorCode = ErrorCode.C409_ORDER_PRICE_CHANGED;

                //when, then
                assertThatThrownBy(() -> CommonValidator.validateEqual(10_000L, 9_000L, errorCode))
                        .isInstanceOf(CustomException.class)
                        .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(errorCode));
            }

            @Test
            public void 한쪽만_null이면_예외가_발생한다() {
                //given
                UUID value = UUID.randomUUID();

                //when, then
                assertThatThrownBy(() -> CommonValidator.validateEqual(null, value, ERROR_CODE))
                        .isInstanceOf(CustomException.class);
                assertThatThrownBy(() -> CommonValidator.validateEqual(value, null, ERROR_CODE))
                        .isInstanceOf(CustomException.class);
            }
        }
    }

    @Nested
    @DisplayName("validateNotEqual()")
    class ValidateNotEqual {

        @Nested
        @DisplayName("정상 케이스")
        class Success {

            @Test
            public void 다른_UUID면_예외가_발생하지_않는다() {
                //given
                UUID expected = UUID.randomUUID();
                UUID actual = UUID.randomUUID();

                //when, then
                assertThatCode(() -> CommonValidator.validateNotEqual(expected, actual, ERROR_CODE))
                        .doesNotThrowAnyException();
            }

            @Test
            public void 한쪽만_null이면_예외가_발생하지_않는다() {
                //given
                UUID value = UUID.randomUUID();

                //when, then
                assertThatCode(() -> CommonValidator.validateNotEqual(null, value, ERROR_CODE))
                        .doesNotThrowAnyException();
                assertThatCode(() -> CommonValidator.validateNotEqual(value, null, ERROR_CODE))
                        .doesNotThrowAnyException();
            }
        }

        @Nested
        @DisplayName("실패 케이스")
        class Failure {

            @Test
            public void 같은_UUID면_전달한_ErrorCode로_예외가_발생한다() {
                //given
                ErrorCode errorCode = ErrorCode.C400_SAME_COMPANY;
                UUID expected = UUID.randomUUID();
                UUID actual = UUID.fromString(expected.toString());

                //when, then
                assertThatThrownBy(() -> CommonValidator.validateNotEqual(expected, actual, errorCode))
                        .isInstanceOf(CustomException.class)
                        .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(errorCode));
            }

            @Test
            public void 둘다_null이면_예외가_발생한다() {
                //when, then
                assertThatThrownBy(() -> CommonValidator.validateNotEqual(null, null, ERROR_CODE))
                        .isInstanceOf(CustomException.class);
            }
        }
    }
}

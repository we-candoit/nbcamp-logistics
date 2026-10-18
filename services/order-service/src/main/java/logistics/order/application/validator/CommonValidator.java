package logistics.order.application.validator;

import logistics.order.presentation.exception.CustomException;
import logistics.order.presentation.exception.ErrorCode;

import java.util.Objects;

public class CommonValidator {

    public static <T> void validateEqual(T expected, T actual, ErrorCode error) {
        if (!Objects.equals(expected, actual)) {
            throw new CustomException(error);
        }
    }

    public static <T> void validateNotEqual(T expected, T actual, ErrorCode error) {
        if (Objects.equals(expected, actual)) {
            throw new CustomException(error);
        }
    }

}

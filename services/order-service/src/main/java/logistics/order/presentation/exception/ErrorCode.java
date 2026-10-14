package logistics.order.presentation.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    //400 BAD REQUEST
    C400_INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST),
    C400_BAD_REQUEST(HttpStatus.BAD_REQUEST),

    //401 UNAUTHORIZE
    C401_MISSING_USER_HEADER(HttpStatus.UNAUTHORIZED),
    C401_MISSING_ROLE_HEADER(HttpStatus.UNAUTHORIZED),

    //403 FORBIDDEN
    C403_NO_AUTHORIZATION(HttpStatus.FORBIDDEN),

    //404 NOT FOUND
    C404_USER_NOT_FOUND(HttpStatus.NOT_FOUND),
    C404_MENU_NOT_FOUND(HttpStatus.NOT_FOUND),
    C404_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND),

    //409 CONFLICT
    C409_DUPLICATE_USER(HttpStatus.CONFLICT);

    private final HttpStatus status;
}
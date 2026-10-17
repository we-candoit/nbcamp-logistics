package logistics.order.presentation.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    //400 BAD REQUEST
    C400_REQUESTER_NOT_FOUND(HttpStatus.BAD_REQUEST),
    C400_SAME_COMPANY(HttpStatus.BAD_REQUEST),

    //401 UNAUTHORIZE
    C401_MISSING_USER_HEADER(HttpStatus.UNAUTHORIZED),
    C401_MISSING_ROLE_HEADER(HttpStatus.UNAUTHORIZED),

    //403 FORBIDDEN
    C403_FORBIDDEN_REQUESTER(HttpStatus.FORBIDDEN),

    //409 CONFLICT
    C409_ORDER_PRICE_CHANGED(HttpStatus.CONFLICT);

    private final HttpStatus status;
}
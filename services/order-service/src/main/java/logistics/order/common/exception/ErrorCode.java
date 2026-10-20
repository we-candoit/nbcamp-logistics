package logistics.order.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    //400 BAD REQUEST
    C400_REQUESTER_NOT_FOUND(HttpStatus.BAD_REQUEST),
    C400_SAME_COMPANY(HttpStatus.BAD_REQUEST),
    C400_BAD_REQUEST(HttpStatus.BAD_REQUEST),

    //401 UNAUTHORIZE
    C401_MISSING_USER_HEADER(HttpStatus.UNAUTHORIZED),
    C401_MISSING_ROLE_HEADER(HttpStatus.UNAUTHORIZED),

    //403 FORBIDDEN
    C403_FORBIDDEN_REQUESTER(HttpStatus.FORBIDDEN),
    C403_FORBIDDEN_ORDER(HttpStatus.FORBIDDEN),

    //404 NOT FOUND
    C404_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND),
    C404_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND),
    C404_COMPANY_NOT_FOUND(HttpStatus.NOT_FOUND),
    C404_USER_NOT_FOUND(HttpStatus.NOT_FOUND),
    C404_DELIVERY_NOT_FOUND(HttpStatus.NOT_FOUND),


    //409 CONFLICT
    C409_ORDER_PRICE_CHANGED(HttpStatus.CONFLICT),
    C409_INVALID_ORDER_STATUS(HttpStatus.CONFLICT),
    C409_ORDER_NOT_CANCELABLE(HttpStatus.CONFLICT),
    C409_ORDER_NOT_DELETABLE(HttpStatus.CONFLICT),

    //500 INTERNAL SERVER ERROR
    C500_INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);


    private final HttpStatus status;
}
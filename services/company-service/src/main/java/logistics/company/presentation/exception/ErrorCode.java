package logistics.company.presentation.exception;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum ErrorCode {
    // 기본
    BAD_REQUEST(400, "Bad Request"),
    UNAUTHORIZED(401, "Unauthorized"),
    FORBIDDEN(403, "FORBIDDEN"),
    NOT_FOUND(404, "Not Found"),

    DUPLICATE_DATA(409, "이미 존재하는 데이터입니다."),
    INTERNAL_SERVER_ERROR(500, "서버 오류가 발생했습니다."),
    EXTERNAL_SERVICE_UNAVAILABLE(503, "연동 서비스 호출에 실패했습니다."),

    // 업체
    COMPANY_CREATE_FORBIDDEN(403, "업체 등록 권한이 없습니다."),
    COMPANY_NOT_FOUND(404, "업체를 찾을 수 없습니다."),
    MANAGER_ALREADY_ASSIGNED(409, "이미 다른 업체를 담당하고 있는 사용자입니다."),

    COMPANY_UPDATE_FORBIDDEN(403, "업체 수정 권한이 없습니다."),
    NO_UPDATE_FIELDS(400, "변경할 내용이 없습니다."),

    COMPANY_HAS_ACTIVE_ORDERS(409, "진행 중인 주문이 있어 업체를 삭제할 수 없습니다."),
    COMPANY_HAS_REMAINING_STOCK(409, "재고가 남아 있어 업체를 삭제할 수 없습니다."),

    // 사용자 확인
    MANAGER_NOT_COMPANY_USER(400, "업체 소속 사용자가 아닙니다."),
    MANAGER_ALREADY_CONFIRMED(409, "이미 소속이 확정된 사용자입니다."),

    // 허브
    HUB_NOT_FOUND(404, "존재하지 않는 허브입니다."),

    // 상품
    ITEM_NOT_FOUND(404, "상품을 찾을 수 없습니다."),
    ITEM_HAS_ACTIVE_ORDERS(409, "진행 중인 주문이 있어 상품을 삭제할 수 없습니다."),
    ITEM_HAS_REMAINING_STOCK(409, "재고가 남아 있어 상품을 삭제할 수 없습니다.");

    // 에러 추가 예정

    private final int statusCode;
    private final String message;
}

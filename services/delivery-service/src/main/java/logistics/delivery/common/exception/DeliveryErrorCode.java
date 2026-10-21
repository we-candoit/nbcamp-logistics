package logistics.delivery.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DeliveryErrorCode {

    // 요청, 헤더
    USER_ID_REQUIRED("사용자 ID 헤더가 필요합니다."),
    INVALID_USER_ID("사용자 ID는 UUID 형식이어야 합니다."),
    ROLE_REQUIRED("역할 헤더가 필요합니다."),
    REQUESTER_REQUIRED("요청자 정보를 확인할 수 없습니다."),
    VALIDATION_FAILED("요청값을 확인해주세요."),
    INVALID_REQUEST_BODY("요청 본문의 형식이 올바르지 않습니다."),
    INVALID_PARAMETER("요청 파라미터가 올바르지 않습니다."),
    INVALID_PAGE("페이지 번호는 0 이상이어야 합니다."),
    INVALID_PAGE_SIZE("페이지 크기는 1 ~ 100이어야 합니다."),
    INVALID_SORT("정렬은 createdAt 또는 updatedAt과 asc 또는 desc로 지정해야 합니다."),

    // 권한
    ACCESS_DENIED("허용되지 않은 권한입니다."),
    MANAGER_ASSIGNMENT_FORBIDDEN("배송 담당자 배정은 MASTER만 가능합니다."),
    ROUTE_STATUS_UPDATE_FORBIDDEN("해당 배송 경로의 담당자만 상태를 변경할 수 있습니다."),

    // 조회
    DELIVERY_NOT_FOUND("배송을 찾을 수 없습니다."),
    DELIVERY_ROUTE_NOT_FOUND("배송 경로를 찾을 수 없습니다."),

    // 생성
    DELETED_DELIVERY_RECREATION_NOT_ALLOWED("이미 삭제된 배송은 다시 생성할 수 없습니다."),
    DELIVERY_CREATION_CONFLICT("같은 주문에 다른 내용의 배송이 이미 존재합니다."),
    DELIVERY_CREATION_BLOCKED("취소 또는 삭제 처리된 주문의 배송은 생성할 수 없습니다."),

    // 취소, 삭제
    DELIVERY_CANCELLATION_NOT_ALLOWED("출발 전 배송만 취소할 수 있습니다."),
    DELIVERY_ROUTE_CANCELLATION_NOT_ALLOWED("출발 전 배송 경로만 취소할 수 있습니다."),
    DELIVERY_DELETION_NOT_ALLOWED("취소된 배송만 삭제할 수 있습니다."),

    // 담당자 배정
    MANAGER_ID_REQUIRED("배정할 담당자 ID가 필요합니다."),
    DELIVERY_ASSIGNMENT_NOT_ALLOWED("현재 배송 상태에서는 담당자를 배정할 수 없습니다."),
    DELIVERY_ROUTE_ASSIGNMENT_NOT_ALLOWED("대기 중인 배송 경로에만 담당자를 배정할 수 있습니다."),
    MANAGER_ALREADY_ASSIGNED("이미 다른 담당자가 배정되어 있습니다."),
    ASSIGNEE_NOT_FOUND("배정할 사용자를 찾을 수 없습니다."),
    ASSIGNEE_NOT_ELIGIBLE("소속이 확정된 허브 배송 담당자만 배정할 수 있습니다."),
    ASSIGNEE_HUB_MISMATCH("배송 경로의 출발 허브에 소속된 담당자만 배정할 수 있습니다."),

    // 상태 변경
    UNSUPPORTED_ROUTE_STATUS("배송 경로 상태는 IN_TRANSIT 또는 ARRIVED로 요청해야 합니다."),
    CANCELLED_DELIVERY_STATUS_CHANGE("취소된 배송은 상태를 변경할 수 없습니다."),
    DELETED_DELIVERY_STATUS_CHANGE("삭제된 배송은 상태를 변경할 수 없습니다."),
    INACTIVE_ROUTE_STATUS_CHANGE("삭제되거나 취소된 배송 경로는 상태를 변경할 수 없습니다."),
    ROUTE_MANAGER_REQUIRED("담당자가 배정된 배송 경로만 출발할 수 있습니다."),
    INVALID_ROUTE_STATUS_TRANSITION("허용되지 않은 배송 경로 상태 변경입니다."),
    INVALID_DELIVERY_STATUS_TRANSITION("현재 배송 상태에서는 해당 상태로 변경할 수 없습니다."),
    PREVIOUS_ROUTES_NOT_FOUND("이전 배송 경로를 확인할 수 없습니다."),
    PREVIOUS_ROUTES_NOT_ARRIVED("이전 배송 경로가 모두 도착해야 출발할 수 있습니다."),

    // 외부 API 연동
    ORDER_SERVICE_UNAVAILABLE("주문자 정보를 확인할 수 없습니다."),
    INVALID_ORDER_SERVICE_RESPONSE("주문 서비스의 응답을 확인할 수 없습니다."),
    USER_SERVICE_UNAVAILABLE("사용자 정보를 확인할 수 없습니다."),
    INVALID_USER_SERVICE_RESPONSE("사용자 서비스의 응답을 확인할 수 없습니다."),
    HUB_SERVICE_UNAVAILABLE("허브 이동 정보를 확인할 수 없습니다."),
    INVALID_HUB_SERVICE_RESPONSE("허브 서비스의 응답을 확인할 수 없습니다."),

    // HTTP 요청 처리
    ENDPOINT_NOT_FOUND("요청한 API를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED("지원하지 않는 HTTP 메서드입니다."),
    UNSUPPORTED_MEDIA_TYPE("지원하지 않는 Content-Type입니다."),
    NOT_ACCEPTABLE("지원하지 않는 응답 형식입니다."),
    HTTP_REQUEST_FAILED("요청을 처리할 수 없습니다."),

    // 예상하지 못한 오류
    INTERNAL_SERVER_ERROR("요청 처리 중 오류가 발생했습니다.");

    private final String message;
}
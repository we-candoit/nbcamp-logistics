package logistics.delivery.domain.enums;

public enum DeliveryOrderControlStatus {
    ALLOWED,        // 배송 생성을 허용
    CANCELLED,      // 주문 취소 완료 -> 배송 생성 차단
    DELETED         // 주문 삭제 완료 -> 배송 생성 차단
}

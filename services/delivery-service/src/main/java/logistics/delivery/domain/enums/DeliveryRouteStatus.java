package logistics.delivery.domain.enums;

public enum DeliveryRouteStatus {
    WAITING,        // 구간 대기
    IN_TRANSIT,     // 구간 이동 중
    ARRIVED,        // 구간 도착
    CANCELLED       // 구간 배송 취소
}

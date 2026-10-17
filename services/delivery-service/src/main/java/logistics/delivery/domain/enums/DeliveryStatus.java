package logistics.delivery.domain.enums;

public enum DeliveryStatus {
    WAITING,            // 배송 대기, default
    HUB_IN_TRANSIT,     // 허브 간 이동 중
    HUB_ARRIVED,        // 목적지 허브 도착
    COMPANY_IN_TRANSIT, // 업체 배송
    DELIVERED,          // 배송 완료
    CANCELLED           // 배송 취소
}

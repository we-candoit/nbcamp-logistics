package logistics.order.infrastructure.client.enums;

//주문ID로 배송취소 결과 / CANCELLED: 배송 취소, CREATION_BLOCKED: 배송이 없어 이후 생성을 차단
public enum DeliveryCancelResult {
    CANCELLED, CREATION_BLOCKED
}

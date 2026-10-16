package logistics.delivery.application;


import logistics.delivery.presentation.dto.response.DeliveryCreateResponse;

// 내부 API - 서비스 사이에서만 사용, 배송의 신규 생성 결과
public record DeliveryCreateResult(
        DeliveryCreateResponse response,
        boolean created
) {
}

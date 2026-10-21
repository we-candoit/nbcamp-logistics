package logistics.company.presentation.dto.response;

import logistics.company.domain.entity.CompanyItem;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CompanyItemDetailResponse {

    private UUID itemId;                        // 상품 id
    private UUID companyId;                     // 소속 업체 id
    private String companyName;                 // 소속 업체명
    private String name;                        // 상품명
    private Long price;                         // 상품 가격
    private String description;                 // 상품 설명
    private List<StockResponse> stocks;         // 허브별 재고 (hub 조회 실패 시 null)
    private Integer totalQuantity;              // 전체 재고 합계 (hub 조회 실패 시 null)
    private LocalDateTime createdAt;            // 생성 시각
    private LocalDateTime updatedAt;            // 수정 시각

    @Getter
    @AllArgsConstructor
    public static class StockResponse {
        private UUID hubId;                     // 재고 보관 허브 id
        private Integer quantity;               // 해당 허브 재고 수량
    }

    // stocks가 null이면 hub-service 조회 실패로 보고 재고 정보만 비워서 응답
    public static CompanyItemDetailResponse of(CompanyItem item, List<StockResponse> stocks) {
        Integer totalQuantity = stocks == null
                ? null
                : stocks.stream().mapToInt(StockResponse::getQuantity).sum();

        return new CompanyItemDetailResponse(
                item.getId(),
                item.getCompany().getId(),
                item.getCompany().getName(),
                item.getName(),
                item.getPrice(),
                item.getDescription(),
                stocks,
                totalQuantity,
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}

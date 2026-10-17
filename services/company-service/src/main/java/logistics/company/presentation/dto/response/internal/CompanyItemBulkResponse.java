package logistics.company.presentation.dto.response.internal;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CompanyItemBulkResponse {

    private List<CompanyItemInternalResponse> items;   // 조회된 상품 목록 (없으면 빈 배열)
    private List<UUID> notFoundIds;                    // 없거나 삭제된 상품 id (전부 있으면 빈 배열)

    public static CompanyItemBulkResponse of(List<CompanyItemInternalResponse> items, List<UUID> notFoundIds) {
        return new CompanyItemBulkResponse(items, notFoundIds);
    }
}
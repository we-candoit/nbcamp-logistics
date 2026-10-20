package logistics.company.presentation.dto.response.internal;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CompanyBulkResponse {

    private List<CompanyInternalResponse> companies;    // 조회된 업체 목록 (없으면 빈 배열)
    private List<UUID> notFoundIds;                     // 없거나 삭제된 업체 id (전부 있으면 빈 배열)

    public static CompanyBulkResponse of(List<CompanyInternalResponse> companies, List<UUID> notFoundIds) {
        return new CompanyBulkResponse(companies, notFoundIds);
    }
}
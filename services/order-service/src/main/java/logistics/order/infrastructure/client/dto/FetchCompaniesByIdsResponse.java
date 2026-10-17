package logistics.order.infrastructure.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor @AllArgsConstructor
public class FetchCompaniesByIdsResponse {
    private List<Company> companies;
    private List<UUID> notFoundIds;

    @Getter
    @NoArgsConstructor @AllArgsConstructor
    public static class Company {

        private UUID companyId;
        private UUID hubId;
        private UUID managerUserId;
        private String name;
        private String address;

    }
}
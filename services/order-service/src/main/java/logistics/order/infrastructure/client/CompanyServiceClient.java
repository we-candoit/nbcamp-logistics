package logistics.order.infrastructure.client;

import jakarta.ws.rs.QueryParam;
import logistics.order.infrastructure.client.dto.FetchCompaniesByIdsResponse;
import logistics.order.infrastructure.client.dto.FetchItemsByIdsResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "${COMPANY_SERVICE_NAME}")
public interface CompanyServiceClient {

    @GetMapping("/internal/companies")
    FetchCompaniesByIdsResponse fetchCompaniesByIds(@QueryParam("ids") List<UUID> companyIds);

    @GetMapping("/internal/items")
    FetchItemsByIdsResponse fetchItemsByIds(@QueryParam("ids") List<UUID> itemIds);
}
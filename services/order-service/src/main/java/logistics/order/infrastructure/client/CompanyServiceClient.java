package logistics.order.infrastructure.client;

import logistics.order.infrastructure.client.dto.FetchCompaniesByIdResponse;
import logistics.order.infrastructure.client.dto.FetchItemsByIdsResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "${COMPANY_SERVICE_NAME}")
public interface CompanyServiceClient {

    @GetMapping("/internal/items")
    FetchItemsByIdsResponse fetchItemsByIds(@RequestParam("ids") List<UUID> itemIds);

    @GetMapping("/api/companies/{companyId}")
    FetchCompaniesByIdResponse fetchCompaniesById(@PathVariable("companyId") UUID companyId);
}
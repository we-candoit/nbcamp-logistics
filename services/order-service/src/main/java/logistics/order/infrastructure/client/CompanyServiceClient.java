package logistics.order.infrastructure.client;

import logistics.order.infrastructure.client.dto.FetchCompaniesByIdsResponse;
import logistics.order.infrastructure.client.dto.FetchItemsByIdsResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "${COMPANY_SERVICE_NAME}")
public interface CompanyServiceClient {

    @GetMapping("/internal/companies")
    FetchCompaniesByIdsResponse fetchCompaniesByIds(@RequestParam("ids") List<UUID> companyIds);

    @GetMapping("/internal/items")
    FetchItemsByIdsResponse fetchItemsByIds(@RequestParam("ids") List<UUID> itemIds);
}
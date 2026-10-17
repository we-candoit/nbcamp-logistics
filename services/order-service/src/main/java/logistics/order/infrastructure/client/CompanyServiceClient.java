package logistics.order.infrastructure.client;

import logistics.order.infrastructure.client.dto.FetchCompanyByIdResponse;
import logistics.order.infrastructure.client.dto.FetchItemByIdResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "${COMPANY_SERVICE_NAME}")
public interface CompanyServiceClient {

    @GetMapping("/api/items/{itemId}")
    FetchItemByIdResponse fetchItemById(@PathVariable("itemId") UUID itemId);

    @GetMapping("/api/companies/{companyId}")
    FetchCompanyByIdResponse fetchCompanyById(@PathVariable("companyId") UUID companyId);
}
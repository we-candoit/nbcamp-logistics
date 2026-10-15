package logistics.order.infrastructure.client;

import logistics.order.infrastructure.dto.GetCompanyResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "${COMPANY_SERVICE_NAME}")
public interface CompanyServiceClient {

    @GetMapping("/api/companies/{companyId}")
    GetCompanyResponse getCompany(@PathVariable("companyId") UUID companyId);

}
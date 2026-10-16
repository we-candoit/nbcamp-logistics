package logistics.order.infrastructure.client;

import jakarta.ws.rs.QueryParam;
import logistics.order.infrastructure.dto.FetchCompaniesByIdsResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "${COMPANY_SERVICE_NAME}")
public interface CompanyServiceClient {

@GetMapping("/api/companies")
FetchCompaniesByIdsResponse fetchCompaniesByIds(@QueryParam("ids") List<UUID> companyIds);

}
package logistics.hub.infrastructure.client;

import logistics.hub.infrastructure.client.dto.CompanyResponse;
import logistics.hub.infrastructure.client.dto.ProductResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

@FeignClient(name = "company-service")
public interface CompanyClient {

    @GetMapping("/api/companies/{companyId}")
    CompanyResponse getCompany(
            @PathVariable("companyId") UUID companyId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-Role-Name") String role
    );

    @GetMapping("/api/items/{itemId}")
    ProductResponse getProduct(
            @PathVariable("itemId") UUID productId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-Role-Name") String role
    );
}

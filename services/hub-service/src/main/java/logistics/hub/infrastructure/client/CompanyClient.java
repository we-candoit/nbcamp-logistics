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
            //권한 설정 필요 - 검증된 사용자의 Bearer 토큰을 Company 서비스에 전달
            @RequestHeader("Authorization") String authorization
    );

    @GetMapping("/api/companies/{companyId}/item/{itemId}")
    ProductResponse getProduct(
            @PathVariable("companyId") UUID companyId,
            @PathVariable("itemId") UUID productId,
            @RequestHeader("Authorization") String authorization
    );
}
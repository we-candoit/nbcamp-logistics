package logistics.user.infrastructure.client;

import logistics.user.common.constant.UserHeaders;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient("${COMPANY_SERVICE_NAME}")
public interface CompanyClient {

    @GetMapping("/api/companies")
    CompanyPageResponse searchCompanies(
            @RequestParam(name = "companyName") String companyName,
            @RequestParam(name = "size") int size,
            @RequestHeader(UserHeaders.USER_ID) UUID userId,
            @RequestHeader(UserHeaders.ROLE_NAME) String roleName
    );
}
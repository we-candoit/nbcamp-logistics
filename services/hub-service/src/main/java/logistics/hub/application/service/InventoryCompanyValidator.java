package logistics.hub.application.service;

import feign.FeignException;
import logistics.hub.infrastructure.client.CompanyClient;
import logistics.hub.infrastructure.client.dto.CompanyResponse;
import logistics.hub.infrastructure.client.dto.ProductResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryCompanyValidator {

    private final CompanyClient companyClient;

    public void validate(
            UUID hubId,
            UUID companyId,
            UUID productId,
            UUID userId,
            String role
    ) {
        CompanyResponse company;
        ProductResponse product;

        try {
            company = companyClient.getCompany(companyId, userId, role);
            product = companyClient.getProduct(productId, userId, role);
        } catch (FeignException.NotFound e) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "업체 또는 상품을 찾을 수 없습니다.",
                    e
            );
        } catch (FeignException.Unauthorized | FeignException.Forbidden e) {
            log.error("Company Service 인증/권한 연동 실패: status={}", e.status(), e);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "업체 서비스 간 인증/권한 확인에 실패했습니다.",
                    e
            );
        } catch (FeignException e) {
            log.error("Company Service 호출 실패: status={}", e.status(), e);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "업체 서비스에서 정보를 확인할 수 없습니다.",
                    e
            );
        }

        if (company == null
                || company.getCompanyId() == null
                || company.getHubId() == null
                || product == null
                || product.getItemId() == null
                || product.getCompanyId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "업체 서비스의 응답 형식이 올바르지 않습니다."
            );
        }

        if (!companyId.equals(company.getCompanyId())
                || !productId.equals(product.getItemId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "요청한 업체 또는 상품과 응답 정보가 다릅니다."
            );
        }

        if (!companyId.equals(product.getCompanyId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "해당 업체에 소속된 상품이 아닙니다."
            );
        }

        if (!hubId.equals(company.getHubId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "해당 업체는 요청한 허브에 소속되어 있지 않습니다."
            );
        }
    }
}

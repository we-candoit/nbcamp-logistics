package logistics.company.application.service;

import jakarta.validation.Valid;
import logistics.company.domain.entity.Company;
import logistics.company.infrastructure.repository.CompanyRepository;
import logistics.company.presentation.dto.request.company.CompanyCreateRequest;
import logistics.company.presentation.dto.response.CompanyResponse;
import logistics.company.presentation.exception.BusinessException;
import logistics.company.presentation.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;

    private static final String ROLE_MASTER = "MASTER";

    public CompanyResponse createCompany(String role, @Valid CompanyCreateRequest request) {
        // 1. 권한 체크: MASTER만 업체 등록 가능
        if(!ROLE_MASTER.equals(role)) {
            throw new BusinessException(ErrorCode.COMPANY_CREATE_FORBIDDEN);
        }

        // 2. 담당장 1인 1업체 (삭제되지 않은 업체 기준, 삭제된 업체와는 겹쳐도 무관)
        UUID managerUserId = request.getManagerUserId();
        if(companyRepository.existsByManagerUserId(managerUserId)) {
            throw new BusinessException(ErrorCode.MANAGER_ALREADY_ASSIGNED);
        }

        // Todo (FeignClient 연동): 허브 존재 확인, 사용자 존재·COMPANY·PENDING 확인 필요

        Company company = Company.create(
                request.getHubId(), managerUserId, request.getName(), request.getAddress()
        );

        // 즉시 INSERT → 동시 요청으로 인덱스 위반이 나면 여기서 예외 발생(409 -> GlobalExceptionHandler)
        Company saved = companyRepository.saveAndFlush(company);

        // Todo (FeignClient 연동): user-service 소속 확정 호출

        return CompanyResponse.from(saved);
    }
}

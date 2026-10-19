package logistics.company.application.service;

import jakarta.validation.Valid;
import logistics.company.domain.entity.Company;
import logistics.company.infrastructure.client.HubClient;
import logistics.company.infrastructure.client.UserClient;
import logistics.company.infrastructure.client.dto.UserResponse;
import logistics.company.infrastructure.repository.CompanyRepository;
import logistics.company.infrastructure.repository.CompanySpecification;
import logistics.company.presentation.dto.request.company.CompanyCreateRequest;
import logistics.company.presentation.dto.request.company.CompanySearchCondition;
import logistics.company.presentation.dto.response.CompanyResponse;
import logistics.company.presentation.exception.BusinessException;
import logistics.company.presentation.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final HubClient hubClient;
    private final UserClient userClient;

    private static final String MASTER = "MASTER";
    private static final String COMPANY = "COMPANY";
    private static final String PENDING = "PENDING";

    @Transactional
    public CompanyResponse createCompany(String role, @Valid CompanyCreateRequest request) {
        // 1. 권한 체크: MASTER만 업체 등록 가능
        if(!MASTER.equals(role)) {
            throw new BusinessException(ErrorCode.COMPANY_CREATE_FORBIDDEN);
        }

        // 2. 담당장 1인 1업체 (삭제되지 않은 업체 기준, 삭제된 업체와는 겹쳐도 무관)
        UUID managerUserId = request.getManagerUserId();
        if(companyRepository.existsByManagerUserId(managerUserId)) {
            throw new BusinessException(ErrorCode.MANAGER_ALREADY_ASSIGNED);
        }

        // 3. 허브 존재 확인 (실패 시 FeignException → GlobalExceptionHandler)
        // TODO(hub-service 실패 응답 확인 필요)
        hubClient.getHub(request.getHubId());

        // 4.관리자로 등록 가능한 사용자 여부 체크 :담당자 존재/COMPANY/PENDING 확인(affiliationType == COMPANY, affiliationStatus == PENDING)
        UserResponse manager = userClient.getUser(request.getManagerUserId()).getData();
        validateManager(manager);

        // 5. 업체 엔티티 생성
        Company company = Company.create(
                request.getHubId(), managerUserId, request.getName(), request.getAddress()
        );
        // 즉시 INSERT → 동시 요청으로 인덱스 위반이 나면 여기서 예외 발생(409 -> GlobalExceptionHandler)
        Company saved = companyRepository.saveAndFlush(company);

        // 6. 소속 확정(companyId 갱신, CONFIRMED, 소속 업체명(affiliationName) 갱신, 유저 확정 실패하면 생성한 업체는 삭제로 보상
        // Todo (FeignClient 연동): user-service 소속 확정 호출

        return CompanyResponse.from(saved);
    }

    private void validateManager(UserResponse manager) {        // 관리자로 등록 가능한 사용자 여부 체크
        if (!COMPANY.equals(manager.getAffiliationType())) {    // affiliationType == COMPANY 체크
            throw new BusinessException(ErrorCode.MANAGER_NOT_COMPANY_USER);
        }
        if (!PENDING.equals(manager.getAffiliationStatus())) {                  // affiliationStatus == PENDING 체크
            throw new BusinessException(ErrorCode.MANAGER_ALREADY_CONFIRMED);   // 이미 소속이 확정된 사용자
        }
    }

    // 업체 목록 검색 (업체명·주소 부분 일치, 페이징)
    @Transactional(readOnly = true)
    public Page<CompanyResponse> searchCompanies(CompanySearchCondition condition, Pageable pageable) {
        return companyRepository
                .findAll(CompanySpecification.search(condition.getCompanyName(), condition.getAddress()), pageable)
                .map(CompanyResponse::from);
    }

    // 업체 상세 조회
    @Transactional(readOnly = true)
    public CompanyResponse getCompany(UUID companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_NOT_FOUND));
        return CompanyResponse.from(company);
    }
}

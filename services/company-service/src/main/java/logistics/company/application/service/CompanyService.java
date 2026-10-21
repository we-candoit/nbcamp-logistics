package logistics.company.application.service;

import logistics.company.domain.entity.Company;
import logistics.company.infrastructure.client.HubClient;
import logistics.company.infrastructure.client.UserClient;
import logistics.company.infrastructure.client.dto.UserAffiliationUpdateRequest;
import logistics.company.infrastructure.client.dto.UserResponse;
import logistics.company.infrastructure.repository.CompanyRepository;
import logistics.company.infrastructure.repository.CompanySpecification;
import logistics.company.presentation.dto.request.company.CompanyCreateRequest;
import logistics.company.presentation.dto.request.company.CompanySearchCondition;
import logistics.company.presentation.dto.request.company.CompanyUpdateRequest;
import logistics.company.presentation.dto.response.CompanyResponse;
import logistics.company.presentation.dto.response.internal.CompanyBulkResponse;
import logistics.company.presentation.dto.response.internal.CompanyInternalResponse;
import logistics.company.presentation.exception.BusinessException;
import logistics.company.presentation.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

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
    public CompanyResponse createCompany(String role, CompanyCreateRequest request) {
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
        userClient.updateAffiliation(managerUserId, UserAffiliationUpdateRequest.confirm(saved.getId(), saved.getName()));

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

    @Transactional
    public CompanyResponse updateCompany(UUID companyId, UUID userId, String role, CompanyUpdateRequest request) {
        // 1. 업체 조회 (없거나 삭제됐으면 404)
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_NOT_FOUND));

        // 2. 권한 체크: MASTER 또는 해당 업체 관리자 본인
        boolean isMaster = MASTER.equals(role);
        if (!isMaster && !company.isManagedBy(userId)) {
            throw new BusinessException(ErrorCode.COMPANY_FORBIDDEN);
        }

        // 3. 기존 값과 비교해 실제로 바뀌는 항목만 판별 (null = 변경 안 함, 수정 취소)
        boolean hubChanged = isChanged(request.getHubId(), company.getHubId());
        boolean nameChanged = isChanged(request.getName(), company.getName());
        boolean addressChanged = isChanged(request.getAddress(), company.getAddress());

        // 바뀌는 항목이 없으면 400 (모두 null이거나 모두 기존과 같은 경우)
        if (!hubChanged && !nameChanged && !addressChanged) {
            throw new BusinessException(ErrorCode.NO_UPDATE_FIELDS);
        }

        // 4. 허브 변경: MASTER 권한 체크 + 허브 존재 확인 (실패 시 403)
        if (hubChanged) {
            if (!isMaster) {
                throw new BusinessException(ErrorCode.COMPANY_FORBIDDEN);
            }
            hubClient.getHub(request.getHubId());
            company.changeHub(request.getHubId());
        }

        // 5. 업체명, 주소 변경
        if (nameChanged) {
            company.changeName(request.getName());
        }
        if (addressChanged) {
            company.changeAddress(request.getAddress());
        }

        // 7. 수정사항 즉시 반영
        companyRepository.flush();

        return CompanyResponse.from(company);
    }

    private boolean isChanged(Object requested, Object current) {
        // 요청 값이 있고, 기존 값과 다르면 변경 대상
        return requested != null && !Objects.equals(requested, current);
    }

    // 내부 API: 업체 복수 조회
    @Transactional(readOnly = true)
    public CompanyBulkResponse getCompanies(List<UUID> ids) {
        // 1. ids 중복 체크
        List<UUID> distinctIds = ids.stream().distinct().toList();

        // 2. ids별 업체 조회
        List<Company> foundCompanies = companyRepository.findAllById(distinctIds);

        // 3. map 형태로 정리
        Map<UUID, Company> companyMap = new HashMap<>();
        for (Company company : foundCompanies) {
            companyMap.put(company.getId(), company);
        }

        // 4. 응답 DTO 구조
        List<CompanyInternalResponse> responses = new ArrayList<>();
        List<UUID> notFoundIds = new ArrayList<>();

        // 5. 찾았으면 CompanyInternalResponse에, 못 찾았으면 id만 notFoundIds 저장
        for (UUID id : distinctIds) {
            Company company = companyMap.get(id);
            if(company != null) {
                responses.add(CompanyInternalResponse.from(company));
            } else {
                notFoundIds.add(id);
            }
        }

        return CompanyBulkResponse.of(responses, notFoundIds);
    }

    public void deleteCompany(UUID companyId, UUID userId, String role) {
        // 1. 삭제할 업체 존재 확인
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_NOT_FOUND));

        // 2. 권한 체크 : MASTER 또는 해당 업체 관리자인지 확인
        if (!MASTER.equals(role) && !company.isManagedBy(userId)) { // MASTER or 해당 업체 관리자가 아닌 경우
            throw new BusinessException(ErrorCode.COMPANY_FORBIDDEN);
        }

        // 3. 논리적 삭제(soft delete: deleted_at, deleted_by 기록)
        company.delete(userId);
    }
}

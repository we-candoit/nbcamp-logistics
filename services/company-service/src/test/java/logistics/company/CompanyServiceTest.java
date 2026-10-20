package logistics.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.withSettings;

import feign.FeignException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import logistics.company.application.service.CompanyService;
import logistics.company.domain.entity.Company;
import logistics.company.infrastructure.client.HubClient;
import logistics.company.infrastructure.client.UserClient;
import logistics.company.infrastructure.client.dto.UserAffiliationUpdateRequest;
import logistics.company.infrastructure.client.dto.UserApiResponse;
import logistics.company.infrastructure.client.dto.UserResponse;
import logistics.company.infrastructure.repository.CompanyRepository;
import logistics.company.presentation.dto.request.company.CompanyCreateRequest;
import logistics.company.presentation.dto.request.company.CompanySearchCondition;
import logistics.company.presentation.dto.response.CompanyResponse;
import logistics.company.presentation.exception.BusinessException;
import logistics.company.presentation.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    private static final UUID HUB_ID = UUID.randomUUID();
    private static final UUID MANAGER_ID = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final String NAME = "스파르타수산";
    private static final String ADDRESS = "독도";
    private static final String MASTER = "MASTER";

    private static final Pageable PAGEABLE =
            PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));

    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private HubClient hubClient;
    @Mock
    private UserClient userClient;

    @InjectMocks
    private CompanyService companyService;

    private CompanyCreateRequest request;

    @BeforeEach
    void setUp() {
        request = lenientMock(CompanyCreateRequest.class);
        given(request.getHubId()).willReturn(HUB_ID);
        given(request.getManagerUserId()).willReturn(MANAGER_ID);
        given(request.getName()).willReturn(NAME);
        given(request.getAddress()).willReturn(ADDRESS);
    }

    // ===== 성공 =====

    @Test
    @DisplayName("성공: 업체 저장 후 담당자 소속을 CONFIRMED로 확정한다")
    void createCompany_success() {
        givenManager("COMPANY", "PENDING");
        givenSavedCompany();

        CompanyResponse response = companyService.createCompany(MASTER, request);

        assertThat(response).isNotNull();

        // 저장 → 소속 확정 순서 확인
        InOrder inOrder = inOrder(companyRepository, userClient);
        inOrder.verify(companyRepository).saveAndFlush(any(Company.class));
        inOrder.verify(userClient).updateAffiliation(eq(MANAGER_ID), any());

        // 소속 확정 요청 내용 확인
        ArgumentCaptor<UserAffiliationUpdateRequest> captor =
                ArgumentCaptor.forClass(UserAffiliationUpdateRequest.class);
        then(userClient).should().updateAffiliation(eq(MANAGER_ID), captor.capture());
        assertThat(captor.getValue().getCompanyId()).isEqualTo(COMPANY_ID);
        assertThat(captor.getValue().getAffiliationName()).isEqualTo(NAME);
    }

    // ===== 내부 검증 실패 =====

    @Test
    @DisplayName("실패: MASTER가 아니면 403, 이후 단계는 실행되지 않는다")
    void createCompany_notMaster() {
        assertBusinessException(
                () -> companyService.createCompany("USER", request),
                ErrorCode.COMPANY_CREATE_FORBIDDEN);

        verifyNoInteractions(companyRepository, hubClient, userClient);
    }

    @Test
    @DisplayName("실패: 담당자가 이미 다른 업체를 맡고 있으면 409, 외부 호출 없음")
    void createCompany_managerAlreadyAssigned() {
        given(companyRepository.existsByManagerUserId(MANAGER_ID)).willReturn(true);

        assertBusinessException(
                () -> companyService.createCompany(MASTER, request),
                ErrorCode.MANAGER_ALREADY_ASSIGNED);

        verifyNoInteractions(hubClient, userClient);
        then(companyRepository).should(never()).saveAndFlush(any());
    }

    // ===== 외부 조회 실패 =====

    @Test
    @DisplayName("실패: 허브가 없으면(404) 예외가 전파되고 저장하지 않는다")
    void createCompany_hubNotFound() {
        given(hubClient.getHub(HUB_ID)).willThrow(FeignException.NotFound.class);

        assertThatThrownBy(() -> companyService.createCompany(MASTER, request))
                .isInstanceOf(FeignException.NotFound.class);

        verifyNoInteractions(userClient);
        then(companyRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("실패: 담당자가 없으면(404) 예외가 전파되고 저장하지 않는다")
    void createCompany_userNotFound() {
        given(userClient.getUser(MANAGER_ID)).willThrow(FeignException.NotFound.class);

        assertThatThrownBy(() -> companyService.createCompany(MASTER, request))
                .isInstanceOf(FeignException.NotFound.class);

        then(companyRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("실패: 담당자가 HUB 소속이면 400, 저장하지 않는다")
    void createCompany_managerNotCompanyUser() {
        givenManager("HUB", "PENDING");

        assertBusinessException(
                () -> companyService.createCompany(MASTER, request),
                ErrorCode.MANAGER_NOT_COMPANY_USER);

        then(companyRepository).should(never()).saveAndFlush(any());
        then(userClient).should(never()).updateAffiliation(any(), any());
    }

    @Test
    @DisplayName("실패: 담당자가 이미 CONFIRMED면 409, 저장하지 않는다")
    void createCompany_managerAlreadyConfirmed() {
        givenManager("COMPANY", "CONFIRMED");

        assertBusinessException(
                () -> companyService.createCompany(MASTER, request),
                ErrorCode.MANAGER_ALREADY_CONFIRMED);

        then(companyRepository).should(never()).saveAndFlush(any());
        then(userClient).should(never()).updateAffiliation(any(), any());
    }

    // ===== 저장 이후 실패 =====

    @Test
    @DisplayName("실패: 동시 요청으로 인덱스 위반 시 예외가 전파되고 소속 확정은 호출되지 않는다")
    void createCompany_uniqueIndexViolation() {
        givenManager("COMPANY", "PENDING");
        given(companyRepository.saveAndFlush(any(Company.class)))
                .willThrow(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> companyService.createCompany(MASTER, request))
                .isInstanceOf(DataIntegrityViolationException.class);

        then(userClient).should(never()).updateAffiliation(any(), any());
    }

    @Test
    @DisplayName("실패: 소속 확정이 실패하면 예외가 전파된다 (@Transactional 롤백 대상)")
    void createCompany_affiliationUpdateFailed() {
        givenManager("COMPANY", "PENDING");
        givenSavedCompany();
        willThrow(FeignException.ServiceUnavailable.class)
                .given(userClient).updateAffiliation(eq(MANAGER_ID), any());

        assertThatThrownBy(() -> companyService.createCompany(MASTER, request))
                .isInstanceOf(FeignException.class);

        // 저장은 시도됐지만 예외가 서비스 밖으로 나가므로 트랜잭션이 롤백된다
        then(companyRepository).should().saveAndFlush(any(Company.class));
    }

    // ===== 상세 조회 =====

    @Test
    @DisplayName("상세 조회 성공: 업체 정보를 CompanyResponse로 반환한다")
    void getCompany_success() {
        Company company = givenCompany();
        given(companyRepository.findById(COMPANY_ID)).willReturn(Optional.of(company));

        CompanyResponse response = companyService.getCompany(COMPANY_ID);

        assertThat(response.getCompanyId()).isEqualTo(COMPANY_ID);
        assertThat(response.getHubId()).isEqualTo(HUB_ID);
        assertThat(response.getManagerUserId()).isEqualTo(MANAGER_ID);
        assertThat(response.getName()).isEqualTo(NAME);
        assertThat(response.getAddress()).isEqualTo(ADDRESS);
    }

    @Test
    @DisplayName("상세 조회 실패: 업체가 없거나 삭제됐으면 404 COMPANY_NOT_FOUND")
    void getCompany_notFound() {
        given(companyRepository.findById(COMPANY_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> companyService.getCompany(COMPANY_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMPANY_NOT_FOUND);
    }

    // ===== 목록 검색 =====

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("목록 검색 성공: 조회 결과를 CompanyResponse 페이지로 변환하고, 페이징 정보를 그대로 전달한다")
    void searchCompanies_success() {
        Company company = givenCompany();
        given(companyRepository.findAll(any(Specification.class), eq(PAGEABLE)))
                .willReturn(new PageImpl<>(List.of(company), PAGEABLE, 1));

        Page<CompanyResponse> result = companyService.searchCompanies(condition("수산", null), PAGEABLE);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo(NAME);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getNumber()).isZero();
        assertThat(result.getSize()).isEqualTo(20);

        then(companyRepository).should().findAll(any(Specification.class), eq(PAGEABLE));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("목록 검색: 결과가 없으면 빈 페이지를 반환한다 (예외 아님)")
    void searchCompanies_empty() {
        given(companyRepository.findAll(any(Specification.class), eq(PAGEABLE)))
                .willReturn(Page.empty(PAGEABLE));

        Page<CompanyResponse> result = companyService.searchCompanies(condition("없는업체", null), PAGEABLE);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    // ===== 헬퍼 =====

    @SuppressWarnings("unchecked")
    private void givenManager(String affiliationType, String affiliationStatus) {
        UserResponse user = lenientMock(UserResponse.class);
        given(user.getAffiliationType()).willReturn(affiliationType);
        given(user.getAffiliationStatus()).willReturn(affiliationStatus);

        UserApiResponse<UserResponse> apiResponse = lenientMock(UserApiResponse.class);
        given(apiResponse.getData()).willReturn(user);

        given(userClient.getUser(MANAGER_ID)).willReturn(apiResponse);
    }

    private void givenSavedCompany() {
        Company saved = lenientMock(Company.class);
        given(saved.getId()).willReturn(COMPANY_ID);
        given(saved.getName()).willReturn(NAME);
        given(companyRepository.saveAndFlush(any(Company.class))).willReturn(saved);
    }

    private void assertBusinessException(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }

    private static <T> T lenientMock(Class<T> type) {
        return mock(type, withSettings().strictness(Strictness.LENIENT));
    }


    private Company givenCompany() {
        Company company = mock(Company.class, withSettings().strictness(Strictness.LENIENT));
        given(company.getId()).willReturn(COMPANY_ID);
        given(company.getHubId()).willReturn(HUB_ID);
        given(company.getManagerUserId()).willReturn(MANAGER_ID);
        given(company.getName()).willReturn(NAME);
        given(company.getAddress()).willReturn(ADDRESS);
        return company;
    }

    private CompanySearchCondition condition(String companyName, String address) {
        CompanySearchCondition condition = new CompanySearchCondition();
        condition.setCompanyName(companyName);
        condition.setAddress(address);
        return condition;
    }
}
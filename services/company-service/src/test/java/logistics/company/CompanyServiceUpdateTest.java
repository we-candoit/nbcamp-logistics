package logistics.company;

import feign.FeignException;
import logistics.company.application.service.CompanyService;
import logistics.company.domain.entity.Company;
import logistics.company.infrastructure.client.HubClient;
import logistics.company.infrastructure.client.UserClient;
import logistics.company.infrastructure.repository.CompanyRepository;
import logistics.company.presentation.dto.request.company.CompanyUpdateRequest;
import logistics.company.presentation.dto.response.CompanyResponse;
import logistics.company.presentation.exception.BusinessException;
import logistics.company.presentation.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyServiceUpdateTest {

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID HUB_ID = UUID.randomUUID();
    private static final UUID NEW_HUB_ID = UUID.randomUUID();
    private static final UUID MANAGER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();
    private static final UUID MASTER_ID = UUID.randomUUID();

    private static final String NAME = "스파르타수산";
    private static final String ADDRESS = "독도";
    private static final String NEW_NAME = "스파르타건어물";
    private static final String NEW_ADDRESS = "울릉도";

    private static final String MASTER = "MASTER";
    private static final String USER = "USER";

    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private HubClient hubClient;
    @Mock
    private UserClient userClient;

    @InjectMocks
    private CompanyService companyService;

    private Company company;

    @BeforeEach
    void setUp() {
        // 실제 엔티티: 수정 후 상태를 직접 확인하기 위함
        company = Company.create(HUB_ID, MANAGER_ID, NAME, ADDRESS);
    }

    // ===== 성공 =====

    @Test
    @DisplayName("MASTER: 업체명·주소·허브를 모두 변경하고, 새 허브의 존재를 확인한다")
    void update_master_allFields() {
        givenCompanyExists();

        CompanyResponse response = companyService.updateCompany(
                COMPANY_ID, MASTER_ID, MASTER, request(NEW_HUB_ID, NEW_NAME, NEW_ADDRESS));

        assertThat(company.getHubId()).isEqualTo(NEW_HUB_ID);
        assertThat(company.getName()).isEqualTo(NEW_NAME);
        assertThat(company.getAddress()).isEqualTo(NEW_ADDRESS);
        assertThat(response.getName()).isEqualTo(NEW_NAME);

        then(hubClient).should().getHub(NEW_HUB_ID);
        then(companyRepository).should().flush();
    }

    @Test
    @DisplayName("업체 관리자 본인: 업체명·주소를 변경할 수 있고, 허브 조회는 하지 않는다")
    void update_manager_nameAndAddress() {
        givenCompanyExists();

        companyService.updateCompany(COMPANY_ID, MANAGER_ID, USER, request(null, NEW_NAME, NEW_ADDRESS));

        assertThat(company.getName()).isEqualTo(NEW_NAME);
        assertThat(company.getAddress()).isEqualTo(NEW_ADDRESS);
        assertThat(company.getHubId()).isEqualTo(HUB_ID);
        verifyNoInteractions(hubClient);
    }

    @Test
    @DisplayName("업체 관리자 본인: 현재와 같은 hubId를 함께 보내도 업체명은 변경된다 (허브는 변경 아님)")
    void update_manager_sameHubIdIsAllowed() {
        givenCompanyExists();

        companyService.updateCompany(COMPANY_ID, MANAGER_ID, USER, request(HUB_ID, NEW_NAME, null));

        assertThat(company.getName()).isEqualTo(NEW_NAME);
        assertThat(company.getHubId()).isEqualTo(HUB_ID);
        verifyNoInteractions(hubClient);
    }

    @Test
    @DisplayName("null 필드는 변경하지 않는다")
    void update_nullFieldsKept() {
        givenCompanyExists();

        companyService.updateCompany(COMPANY_ID, MASTER_ID, MASTER, request(null, NEW_NAME, null));

        assertThat(company.getName()).isEqualTo(NEW_NAME);
        assertThat(company.getAddress()).isEqualTo(ADDRESS);
        assertThat(company.getHubId()).isEqualTo(HUB_ID);
    }

    // ===== 권한 실패 =====

    @Test
    @DisplayName("업체 관리자가 다른 허브로 변경하려 하면 403, 업체명도 함께 변경되지 않는다")
    void update_manager_changeHub_forbidden() {
        givenCompanyExists();

        assertBusinessException(
                () -> companyService.updateCompany(
                        COMPANY_ID, MANAGER_ID, USER, request(NEW_HUB_ID, NEW_NAME, null)),
                ErrorCode.COMPANY_FORBIDDEN);

        assertThat(company.getHubId()).isEqualTo(HUB_ID);
        assertThat(company.getName()).isEqualTo(NAME);
        verifyNoInteractions(hubClient);
        then(companyRepository).should(never()).flush();
    }

    @Test
    @DisplayName("관리자도 MASTER도 아닌 사용자는 403")
    void update_otherUser_forbidden() {
        givenCompanyExists();

        assertBusinessException(
                () -> companyService.updateCompany(
                        COMPANY_ID, OTHER_USER_ID, USER, request(null, NEW_NAME, null)),
                ErrorCode.COMPANY_FORBIDDEN);

        assertThat(company.getName()).isEqualTo(NAME);
        verifyNoInteractions(hubClient);
    }

    // ===== 변경 없음 =====

    @Test
    @DisplayName("모든 필드가 null이면 400 NO_UPDATE_FIELDS")
    void update_allNull() {
        givenCompanyExists();

        assertBusinessException(
                () -> companyService.updateCompany(COMPANY_ID, MASTER_ID, MASTER, request(null, null, null)),
                ErrorCode.NO_UPDATE_FIELDS);

        then(companyRepository).should(never()).flush();
    }

    @Test
    @DisplayName("모든 값이 기존과 같으면 400 NO_UPDATE_FIELDS")
    void update_allSameAsCurrent() {
        givenCompanyExists();

        assertBusinessException(
                () -> companyService.updateCompany(
                        COMPANY_ID, MASTER_ID, MASTER, request(HUB_ID, NAME, ADDRESS)),
                ErrorCode.NO_UPDATE_FIELDS);

        verifyNoInteractions(hubClient);
        then(companyRepository).should(never()).flush();
    }

    // ===== 조회·외부 호출 실패 =====

    @Test
    @DisplayName("업체가 없거나 삭제됐으면 404 COMPANY_NOT_FOUND")
    void update_companyNotFound() {
        given(companyRepository.findById(COMPANY_ID)).willReturn(Optional.empty());

        assertBusinessException(
                () -> companyService.updateCompany(COMPANY_ID, MASTER_ID, MASTER, request(null, NEW_NAME, null)),
                ErrorCode.COMPANY_NOT_FOUND);

        verifyNoInteractions(hubClient);
    }

    @Test
    @DisplayName("변경할 허브가 없으면(404) 예외가 전파되고, 아무 값도 변경되지 않는다")
    void update_hubNotFound() {
        givenCompanyExists();
        given(hubClient.getHub(NEW_HUB_ID)).willThrow(FeignException.NotFound.class);

        assertThatThrownBy(() -> companyService.updateCompany(
                COMPANY_ID, MASTER_ID, MASTER, request(NEW_HUB_ID, NEW_NAME, null)))
                .isInstanceOf(FeignException.NotFound.class);

        assertThat(company.getHubId()).isEqualTo(HUB_ID);
        assertThat(company.getName()).isEqualTo(NAME);
        then(companyRepository).should(never()).flush();
    }

    // ===== 헬퍼 =====

    private void givenCompanyExists() {
        given(companyRepository.findById(COMPANY_ID)).willReturn(Optional.of(company));
    }

    private CompanyUpdateRequest request(UUID hubId, String name, String address) {
        CompanyUpdateRequest request = mock(CompanyUpdateRequest.class, withSettings().strictness(Strictness.LENIENT));
        given(request.getHubId()).willReturn(hubId);
        given(request.getName()).willReturn(name);
        given(request.getAddress()).willReturn(address);
        return request;
    }

    private void assertBusinessException(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }
}
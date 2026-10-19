package logistics.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

import feign.FeignException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import logistics.company.application.service.CompanyService;

import logistics.company.domain.entity.Company;
import logistics.company.infrastructure.client.HubClient;
import logistics.company.infrastructure.client.UserClient;
import logistics.company.infrastructure.client.dto.UserApiResponse;
import logistics.company.infrastructure.client.dto.UserResponse;
import logistics.company.infrastructure.repository.CompanyRepository;
import logistics.company.presentation.dto.request.company.CompanyCreateRequest;
import logistics.company.presentation.exception.BusinessException;
import logistics.company.presentation.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.AuditorAware;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@SpringBootTest(
        properties = {
                // DB: PostgreSQL 대신 H2 메모리 DB, company_schema 스키마를 미리 생성
                "spring.datasource.url=jdbc:h2:mem:company-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS company_schema",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.properties.hibernate.default_schema=company_schema",
                // H2는 부분 인덱스(WHERE)를 지원하지 않으므로 schema.sql 실행 안 함
                "spring.sql.init.mode=never",
                // 외부 연결 차단
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "server.port=0"
        }
)
class CompanyServiceIntegrationTest {

    private static final String MASTER = "MASTER";
    private static final String NAME = "스파르타수산";
    private static final String ADDRESS = "부산 동구 중앙대로 206";

    @Autowired
    private CompanyService companyService;
    @Autowired
    private CompanyRepository companyRepository;

    // 외부 서비스 호출은 가짜로 대체
    @MockitoBean
    private HubClient hubClient;
    @MockitoBean
    private UserClient userClient;

    // 감사 필드(created_by 등): 테스트에는 HTTP 요청이 없으므로 고정값 사용
    @MockitoBean
    private AuditorAware<String> auditorAware;

    private UUID hubId;
    private UUID managerId;

    @BeforeEach
    void setUp() {
        hubId = UUID.randomUUID();
        managerId = UUID.randomUUID();
        given(auditorAware.getCurrentAuditor()).willReturn(Optional.of("test-master"));
    }

    @AfterEach
    void tearDown() {
        companyRepository.deleteAll();
    }

    @Test
    @DisplayName("성공: 업체가 DB에 저장되고, 저장된 ID로 소속 확정을 호출한다")
    void createCompany_savedToDb() {
        givenManager("COMPANY", "PENDING");

        companyService.createCompany(MASTER, request(managerId));

        List<Company> companies = companyRepository.findAll();
        assertThat(companies).hasSize(1);

        Company saved = companies.get(0);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo(NAME);
        assertThat(companyRepository.existsByManagerUserId(managerId)).isTrue();

        then(userClient).should().updateAffiliation(eq(managerId), any());
    }

    @Test
    @DisplayName("롤백: 소속 확정이 실패하면 업체가 DB에 남지 않는다")
    void createCompany_rollbackWhenAffiliationFails() {
        givenManager("COMPANY", "PENDING");
        willThrow(FeignException.ServiceUnavailable.class)
                .given(userClient).updateAffiliation(eq(managerId), any());

        assertThatThrownBy(() -> companyService.createCompany(MASTER, request(managerId)))
                .isInstanceOf(FeignException.class);

        assertThat(companyRepository.count()).isZero();
    }

    @Test
    @DisplayName("실패: 같은 담당자로 다시 등록하면 409, DB에는 1건만 남는다")
    void createCompany_duplicateManager() {
        givenManager("COMPANY", "PENDING");
        companyService.createCompany(MASTER, request(managerId));

        assertThatThrownBy(() -> companyService.createCompany(MASTER, request(managerId)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MANAGER_ALREADY_ASSIGNED);

        assertThat(companyRepository.count()).isEqualTo(1);
    }

    // ===== 헬퍼 =====

    private CompanyCreateRequest request(UUID managerUserId) {
        CompanyCreateRequest request = mock(CompanyCreateRequest.class);
        given(request.getHubId()).willReturn(hubId);
        given(request.getManagerUserId()).willReturn(managerUserId);
        given(request.getName()).willReturn(NAME);
        given(request.getAddress()).willReturn(ADDRESS);
        return request;
    }

    @SuppressWarnings("unchecked")
    private void givenManager(String affiliationType, String affiliationStatus) {
        UserResponse user = mock(UserResponse.class);
        given(user.getAffiliationType()).willReturn(affiliationType);
        given(user.getAffiliationStatus()).willReturn(affiliationStatus);

        UserApiResponse<UserResponse> apiResponse = mock(UserApiResponse.class);
        given(apiResponse.getData()).willReturn(user);

        given(userClient.getUser(managerId)).willReturn(apiResponse);
    }
}
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.AuditorAware;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ActiveProfiles("test")
@SpringBootTest(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:company-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS company_schema",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.properties.hibernate.default_schema=company_schema",
                "spring.sql.init.mode=never",
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "server.port=0",
                // (SQL 로그 확인용, 확인 후 삭제)
//                "spring.jpa.show-sql=true",
//                "spring.jpa.properties.hibernate.format_sql=true"
        }
)
class CompanyUpdateIntegrationTest {

    private static final String MASTER = "MASTER";
    private static final String USER = "USER";
    private static final String CREATOR = "creator-id";
    private static final String EDITOR = "editor-id";

    private static final String NAME = "스파르타수산";
    private static final String ADDRESS = "독도";
    private static final String NEW_NAME = "스파르타건어물";

    @Autowired
    private CompanyService companyService;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private HubClient hubClient;
    @MockitoBean
    private UserClient userClient;
    @MockitoBean
    private AuditorAware<String> auditorAware;

    private UUID hubId;
    private UUID managerId;
    private UUID masterId;
    private Company saved;

    @BeforeEach
    void setUp() {
        hubId = UUID.randomUUID();
        managerId = UUID.randomUUID();
        masterId = UUID.randomUUID();

        // 등록자 = CREATOR
        givenAuditor(CREATOR);
        saved = companyRepository.saveAndFlush(Company.create(hubId, managerId, NAME, ADDRESS));

        // 이후 수정자 = EDITOR
        givenAuditor(EDITOR);
    }

    @AfterEach
    void tearDown() {
        // 삭제된 행까지 지우기 위해 @SQLRestriction을 거치지 않는 SQL로 정리
        jdbcTemplate.update("DELETE FROM company_schema.p_companies");
    }

    @Test
    @DisplayName("수정 내용이 DB에 반영되고, updatedBy·updatedAt이 갱신된다 (createdBy는 유지)")
    void update_savedToDb_withAuditFields() throws InterruptedException {
        LocalDateTime beforeUpdatedAt = reload().getUpdatedAt();
        Thread.sleep(10);   // 수정 시각 차이가 확실히 나도록

        CompanyResponse response = companyService.updateCompany(
                saved.getId(), managerId, USER, request(null, NEW_NAME, null));

        Company reloaded = reload();
        assertThat(reloaded.getName()).isEqualTo(NEW_NAME);
        assertThat(reloaded.getAddress()).isEqualTo(ADDRESS);

        assertThat(reloaded.getCreatedBy()).isEqualTo(CREATOR);
        assertThat(reloaded.getUpdatedBy()).isEqualTo(EDITOR);
        assertThat(reloaded.getUpdatedAt()).isAfter(beforeUpdatedAt);

        // flush 덕분에 응답의 updatedAt이 DB 값과 같다 (DB 정밀도 차이를 고려해 1ms 허용)
        assertThat(response.getUpdatedAt()).isCloseTo(reloaded.getUpdatedAt(), within(1, ChronoUnit.MILLIS));
    }

    @Test
    @DisplayName("권한 실패(403) 시 DB 값이 그대로다")
    void update_forbidden_noChangeInDb() {
        UUID newHubId = UUID.randomUUID();

        assertThatThrownBy(() -> companyService.updateCompany(
                saved.getId(), managerId, USER, request(newHubId, NEW_NAME, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMPANY_UPDATE_FORBIDDEN);

        Company reloaded = reload();
        assertThat(reloaded.getName()).isEqualTo(NAME);
        assertThat(reloaded.getHubId()).isEqualTo(hubId);
        assertThat(reloaded.getUpdatedBy()).isEqualTo(CREATOR);
    }

    @Test
    @DisplayName("허브 존재 확인 실패 시 롤백되어 DB 값이 그대로다")
    void update_hubNotFound_rollback() {
        UUID newHubId = UUID.randomUUID();
        given(hubClient.getHub(newHubId)).willThrow(FeignException.NotFound.class);

        assertThatThrownBy(() -> companyService.updateCompany(
                saved.getId(), masterId, MASTER, request(newHubId, NEW_NAME, null)))
                .isInstanceOf(FeignException.class);

        Company reloaded = reload();
        assertThat(reloaded.getHubId()).isEqualTo(hubId);
        assertThat(reloaded.getName()).isEqualTo(NAME);
    }

    @Test
    @DisplayName("기존과 같은 값이면 400, updatedAt이 바뀌지 않는다")
    void update_sameValues_noUpdate() {
        LocalDateTime beforeUpdatedAt = reload().getUpdatedAt();

        assertThatThrownBy(() -> companyService.updateCompany(
                saved.getId(), masterId, MASTER, request(hubId, NAME, ADDRESS)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NO_UPDATE_FIELDS);

        Company reloaded = reload();
        assertThat(reloaded.getUpdatedAt()).isEqualTo(beforeUpdatedAt);
        assertThat(reloaded.getUpdatedBy()).isEqualTo(CREATOR);
    }

    @Test
    @DisplayName("삭제된 업체는 @SQLRestriction으로 조회되지 않아 404")
    void update_deletedCompany_notFound() {
        Company company = reload();
        company.delete(EDITOR);
        companyRepository.saveAndFlush(company);

        assertThatThrownBy(() -> companyService.updateCompany(
                saved.getId(), masterId, MASTER, request(null, NEW_NAME, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMPANY_NOT_FOUND);
    }

    // ===== 헬퍼 =====

    private Company reload() {
        return companyRepository.findById(saved.getId()).orElseThrow();
    }

    private void givenAuditor(String auditor) {
        given(auditorAware.getCurrentAuditor()).willReturn(Optional.of(auditor));
    }

    private CompanyUpdateRequest request(UUID hubId, String name, String address) {
        CompanyUpdateRequest request = mock(CompanyUpdateRequest.class);
        given(request.getHubId()).willReturn(hubId);
        given(request.getName()).willReturn(name);
        given(request.getAddress()).willReturn(address);
        return request;
    }
}
package logistics.company;

import feign.FeignException;
import logistics.company.application.service.CompanyItemService;
import logistics.company.application.service.CompanyService;
import logistics.company.domain.entity.Company;
import logistics.company.domain.entity.CompanyItem;
import logistics.company.infrastructure.client.UserClient;
import logistics.company.infrastructure.repository.CompanyItemRepository;
import logistics.company.infrastructure.repository.CompanyRepository;
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

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

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
                "server.port=0"
        }
)
class CompanyDeleteIntegrationTest {

    @Autowired private CompanyService companyService;
    @Autowired private CompanyItemService companyItemService;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyItemRepository companyItemRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private AuditorAware<UUID> auditorAware;

    @MockitoBean private UserClient userClient;

    private static final UUID MANAGER_ID = UUID.randomUUID();
    private static final UUID OTHER_MANAGER_ID = UUID.randomUUID();

    private UUID companyId;
    private UUID squidId;          // 업체 삭제 때 함께 삭제될 상품
    private UUID anchovyId;        // 업체 삭제 전에 따로 삭제한 상품
    private UUID otherItemId;      // 다른 업체 상품 (영향 없어야 함)

    @BeforeEach
    void setUp() {
        given(auditorAware.getCurrentAuditor()).willReturn(Optional.of(MANAGER_ID));

        Company company = companyRepository.saveAndFlush(
                Company.create(UUID.randomUUID(), MANAGER_ID, "스파르타수산", "서울특별시 강남구 테헤란로 152"));
        companyId = company.getId();
        squidId = companyItemRepository.saveAndFlush(
                CompanyItem.create(company, "마른오징어", 15000L, "국산")).getId();
        anchovyId = companyItemRepository.saveAndFlush(
                CompanyItem.create(company, "마른멸치", 8000L, "국산")).getId();

        Company other = companyRepository.saveAndFlush(
                Company.create(UUID.randomUUID(), OTHER_MANAGER_ID, "부산건어물", "부산광역시 중구 중앙대로 1"));
        otherItemId = companyItemRepository.saveAndFlush(
                CompanyItem.create(other, "건새우", 12000L, "국산")).getId();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM company_schema.p_company_items");
        jdbcTemplate.execute("DELETE FROM company_schema.p_companies");
    }

    private Map<String, Object> companyRow(UUID id) {
        return jdbcTemplate.queryForMap(
                "SELECT deleted_at, deleted_by FROM company_schema.p_companies WHERE id = ?", id);
    }

    private Map<String, Object> itemRow(UUID id) {
        return jdbcTemplate.queryForMap(
                "SELECT deleted_at, deleted_by FROM company_schema.p_company_items WHERE id = ?", id);
    }

    @Test
    @DisplayName("업체를 삭제하면 업체와 소속 상품이 같은 삭제 시각으로 논리 삭제되고 조회에서 제외된다")
    void deleteCompany_cascadeSoftDelete() {
        companyService.deleteCompany(companyId, MANAGER_ID, "USER");

        Map<String, Object> company = companyRow(companyId);
        Map<String, Object> squid = itemRow(squidId);

        // 업체: 행은 남고 삭제 정보 기록
        assertThat(company.get("deleted_at")).isNotNull();
        assertThat(company.get("deleted_by")).isEqualTo(MANAGER_ID);

        // 상품: 업체와 같은 삭제 시각·삭제자
        assertThat(squid.get("deleted_at")).isEqualTo(company.get("deleted_at"));
        assertThat(squid.get("deleted_by")).isEqualTo(MANAGER_ID);

        // @SQLRestriction으로 조회에서 제외
        assertThat(companyRepository.findById(companyId)).isEmpty();
        assertThat(companyItemRepository.findById(squidId)).isEmpty();
    }

    @Test
    @DisplayName("업체 삭제 전에 따로 삭제한 상품은 삭제 시각이 덮어써지지 않는다 (복구 시 구분 가능)")
    void deleteCompany_keepsPreviouslyDeletedItem() {
        companyItemService.deleteItem(anchovyId, MANAGER_ID, "USER");
        Object anchovyDeletedAt = itemRow(anchovyId).get("deleted_at");

        companyService.deleteCompany(companyId, MANAGER_ID, "USER");

        assertThat(itemRow(anchovyId).get("deleted_at")).isEqualTo(anchovyDeletedAt);
        assertThat(itemRow(anchovyId).get("deleted_at")).isNotEqualTo(companyRow(companyId).get("deleted_at"));
    }

    @Test
    @DisplayName("다른 업체의 상품은 영향을 받지 않는다")
    void deleteCompany_otherCompanyItemUntouched() {
        companyService.deleteCompany(companyId, MANAGER_ID, "USER");

        assertThat(itemRow(otherItemId).get("deleted_at")).isNull();
        assertThat(companyItemRepository.findById(otherItemId)).isPresent();
    }

    @Test
    @DisplayName("권한이 없으면 업체·상품 모두 삭제되지 않는다")
    void deleteCompany_forbidden_nothingDeleted() {
        Throwable thrown = catchThrowable(() -> companyService.deleteCompany(companyId, OTHER_MANAGER_ID, "USER"));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(ErrorCode.COMPANY_FORBIDDEN);
        assertThat(companyRow(companyId).get("deleted_at")).isNull();
        assertThat(itemRow(squidId).get("deleted_at")).isNull();
    }

    @Test
    @DisplayName("이미 삭제된 업체를 다시 삭제하면 COMPANY_NOT_FOUND")
    void deleteCompany_alreadyDeleted() {
        companyService.deleteCompany(companyId, MANAGER_ID, "USER");

        Throwable thrown = catchThrowable(() -> companyService.deleteCompany(companyId, MANAGER_ID, "USER"));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(ErrorCode.COMPANY_NOT_FOUND);
    }

    @Test
    @DisplayName("소속 해제가 실패하면 업체·상품 삭제가 모두 롤백된다")
    void deleteCompany_releaseFail_rollback() {
        willThrow(FeignException.ServiceUnavailable.class)
                .given(userClient).releaseAffiliation(any(), any());

        Throwable thrown = catchThrowable(() -> companyService.deleteCompany(companyId, MANAGER_ID, "USER"));

        assertThat(thrown).isInstanceOf(FeignException.class);
        assertThat(companyRow(companyId).get("deleted_at")).isNull();
        assertThat(itemRow(squidId).get("deleted_at")).isNull();
        assertThat(companyRepository.findById(companyId)).isPresent();
    }
}
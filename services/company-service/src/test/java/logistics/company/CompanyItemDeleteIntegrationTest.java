package logistics.company;

import logistics.company.application.service.CompanyItemService;
import logistics.company.domain.entity.Company;
import logistics.company.domain.entity.CompanyItem;
import logistics.company.infrastructure.client.HubClient;
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
import static org.mockito.BDDMockito.given;

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
class CompanyItemDeleteIntegrationTest {

    @Autowired private CompanyItemService companyItemService;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired private CompanyItemRepository companyItemRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private AuditorAware<UUID> auditorAware;

    private static final UUID MANAGER_ID = UUID.randomUUID();
    private UUID itemId;

    @BeforeEach
    void setUp() {
        given(auditorAware.getCurrentAuditor()).willReturn(Optional.of(MANAGER_ID));

        Company company = companyRepository.saveAndFlush(
                Company.create(UUID.randomUUID(), MANAGER_ID, "스파르타수산", "서울특별시 강남구 테헤란로 152"));
        itemId = companyItemRepository.saveAndFlush(
                CompanyItem.create(company, "마른오징어", 15000L, "국산")).getId();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM company_schema.p_company_items");
        jdbcTemplate.execute("DELETE FROM company_schema.p_companies");
    }

    @Test
    @DisplayName("삭제하면 DB에 deleted_at, deleted_by가 기록되고 조회에서 제외된다")
    void deleteItem_softDelete() {
        companyItemService.deleteItem(itemId, MANAGER_ID, "USER");

        // 행은 남아 있고 삭제 정보가 기록됨 (물리 삭제 아님)
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT deleted_at, deleted_by FROM company_schema.p_company_items WHERE id = ?", itemId);
        assertThat(row.get("deleted_at")).isNotNull();
        assertThat(row.get("deleted_by")).isEqualTo(MANAGER_ID);

        // @SQLRestriction으로 조회에서 제외
        assertThat(companyItemRepository.findById(itemId)).isEmpty();
    }

    @Test
    @DisplayName("이미 삭제된 상품을 다시 삭제하면 ITEM_NOT_FOUND")
    void deleteItem_alreadyDeleted() {
        companyItemService.deleteItem(itemId, MANAGER_ID, "USER");

        Throwable thrown = catchThrowable(() -> companyItemService.deleteItem(itemId, MANAGER_ID, "USER"));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(ErrorCode.ITEM_NOT_FOUND);
    }
}
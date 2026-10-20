package logistics.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import logistics.company.application.service.CompanyItemService;
import logistics.company.application.service.CompanyService;
import logistics.company.domain.entity.Company;
import logistics.company.domain.entity.CompanyItem;
import logistics.company.infrastructure.client.HubClient;
import logistics.company.infrastructure.client.UserClient;
import logistics.company.infrastructure.repository.CompanyItemRepository;
import logistics.company.infrastructure.repository.CompanyRepository;
import logistics.company.presentation.dto.response.internal.CompanyBulkResponse;
import logistics.company.presentation.dto.response.internal.CompanyInternalResponse;
import logistics.company.presentation.dto.response.internal.CompanyItemBulkResponse;
import logistics.company.presentation.dto.response.internal.CompanyItemInternalResponse;
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
class InternalBulkQueryIntegrationTest {

    private static final UUID AUDITOR = UUID.randomUUID() ;

    @Autowired
    private CompanyService companyService;
    @Autowired
    private CompanyItemService companyItemService;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private CompanyItemRepository companyItemRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private HubClient hubClient;
    @MockitoBean
    private UserClient userClient;
    @MockitoBean
    private AuditorAware<UUID> auditorAware;

    private Company active;
    private Company deletedCompany;
    private CompanyItem activeItem;
    private CompanyItem deletedItem;

    @BeforeEach
    void setUp() {
        given(auditorAware.getCurrentAuditor()).willReturn(Optional.of(AUDITOR));

        active = companyRepository.saveAndFlush(
                Company.create(UUID.randomUUID(), UUID.randomUUID(), "스파르타수산", "서울 강남구 테헤란로 311"));
        deletedCompany = companyRepository.saveAndFlush(
                Company.create(UUID.randomUUID(), UUID.randomUUID(), "삭제된업체", "독도"));
        deletedCompany.delete(AUDITOR);
        companyRepository.saveAndFlush(deletedCompany);

        activeItem = companyItemRepository.saveAndFlush(CompanyItem.create(active, "마른오징어", 15000L, null));
        deletedItem = companyItemRepository.saveAndFlush(CompanyItem.create(active, "삭제된상품", 1000L, null));
        deletedItem.delete(AUDITOR);
        companyItemRepository.saveAndFlush(deletedItem);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM company_schema.p_company_items");
        jdbcTemplate.update("DELETE FROM company_schema.p_companies");
    }

    @Test
    @DisplayName("업체: 삭제된 업체는 notFoundIds로 응답한다")
    void companies_deletedExcluded() {
        CompanyBulkResponse response = companyService.getCompanies(List.of(active.getId(), deletedCompany.getId()));

        assertThat(response.getCompanies())
                .extracting(CompanyInternalResponse::getCompanyId)
                .containsExactly(active.getId());
        assertThat(response.getNotFoundIds()).containsExactly(deletedCompany.getId());
    }

    @Test
    @DisplayName("상품: 삭제된 상품은 notFoundIds로 응답하고, 업체 정보가 함께 담긴다")
    void items_deletedExcluded() {
        CompanyItemBulkResponse response = companyItemService.getItems(List.of(activeItem.getId(), deletedItem.getId()));

        assertThat(response.getItems())
                .extracting(CompanyItemInternalResponse::getItemId)
                .containsExactly(activeItem.getId());
        assertThat(response.getItems().get(0).getAddress()).isEqualTo("서울 강남구 테헤란로 311");
        assertThat(response.getNotFoundIds()).containsExactly(deletedItem.getId());
    }

    @Test
    @DisplayName("상품: @EntityGraph로 업체를 함께 조회한다 (트랜잭션 밖에서도 업체 정보 접근 가능)")
    void items_entityGraph() {
        List<CompanyItem> items = companyItemRepository.findAllByIdIn(List.of(activeItem.getId()));

        // 업체를 함께 가져오지 않았다면 트랜잭션 밖이라 LazyInitializationException 발생
        assertThat(items.get(0).getCompany().getAddress()).isEqualTo("서울 강남구 테헤란로 311");
    }
}
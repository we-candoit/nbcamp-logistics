package logistics.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import logistics.company.application.service.CompanyItemService;
import logistics.company.domain.entity.Company;
import logistics.company.domain.entity.CompanyItem;
import logistics.company.infrastructure.client.HubClient;
import logistics.company.infrastructure.client.UserClient;
import logistics.company.infrastructure.repository.CompanyItemRepository;
import logistics.company.infrastructure.repository.CompanyRepository;
import logistics.company.presentation.dto.request.companyItem.CompanyItemCreateRequest;
import logistics.company.presentation.dto.response.CompanyItemResponse;
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
class CompanyItemCreateIntegrationTest {

    private static final String MASTER = "MASTER";
    private static final String AUDITOR = "creator-id";
    private static final String ITEM_NAME = "마른오징어";
    private static final Long PRICE = 15000L;

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
    private AuditorAware<String> auditorAware;

    private UUID masterId;
    private Company company;

    @BeforeEach
    void setUp() {
        given(auditorAware.getCurrentAuditor()).willReturn(Optional.of(AUDITOR));
        masterId = UUID.randomUUID();
        company = saveCompany("스파르타수산");
    }

    @AfterEach
    void tearDown() {
        // 삭제된 행까지 지우기 위해 SQL로 정리 (FK 때문에 상품 → 업체 순서)
        jdbcTemplate.update("DELETE FROM company_schema.p_company_items");
        jdbcTemplate.update("DELETE FROM company_schema.p_companies");
    }

    @Test
    @DisplayName("상품이 DB에 저장된다 (업체 연결, 가격, 감사 필드)")
    void createItem_savedToDb() {
        CompanyItemResponse response = companyItemService.createItem(
                company.getId(), masterId, MASTER, request(ITEM_NAME, PRICE, "국산"));

        List<CompanyItem> items = companyItemRepository.findAll();
        assertThat(items).hasSize(1);

        CompanyItem saved = items.get(0);
        assertThat(saved.getId()).isEqualTo(response.getItemId());
        assertThat(saved.getCompany().getId()).isEqualTo(company.getId());
        assertThat(saved.getName()).isEqualTo(ITEM_NAME);
        assertThat(saved.getPrice()).isEqualTo(PRICE);
        assertThat(saved.getDescription()).isEqualTo("국산");
        assertThat(saved.getCreatedBy()).isEqualTo(AUDITOR);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("상품명은 앞뒤 공백을 제거한 값으로 DB에 저장된다")
    void createItem_nameTrimmedInDb() {
        companyItemService.createItem(
                company.getId(), masterId, MASTER, request("  " + ITEM_NAME + "  ", PRICE, null));

        assertThat(companyItemRepository.findAll())
                .extracting(CompanyItem::getName)
                .containsExactly(ITEM_NAME);
    }

    @Test
    @DisplayName("공백만 다른 같은 이름은 중복(409)이고, DB에는 1건만 남는다")
    void createItem_duplicateAfterTrim() {
        companyItemService.createItem(company.getId(), masterId, MASTER, request(ITEM_NAME, PRICE, null));

        assertThatThrownBy(() -> companyItemService.createItem(
                company.getId(), masterId, MASTER, request(" " + ITEM_NAME + " ", PRICE, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ITEM_NAME_ALREADY_EXISTS);

        assertThat(companyItemRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("대소문자가 다르면 다른 상품으로 저장된다")
    void createItem_caseSensitive() {
        companyItemService.createItem(company.getId(), masterId, MASTER, request("Squid", PRICE, null));
        companyItemService.createItem(company.getId(), masterId, MASTER, request("squid", PRICE, null));

        assertThat(companyItemRepository.findAll())
                .extracting(CompanyItem::getName)
                .containsExactlyInAnyOrder("Squid", "squid");
    }

    @Test
    @DisplayName("다른 업체에는 같은 상품명을 등록할 수 있다")
    void createItem_sameNameInOtherCompany() {
        Company otherCompany = saveCompany("스파르타건어물");

        companyItemService.createItem(company.getId(), masterId, MASTER, request(ITEM_NAME, PRICE, null));
        companyItemService.createItem(otherCompany.getId(), masterId, MASTER, request(ITEM_NAME, PRICE, null));

        assertThat(companyItemRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("삭제된 상품과 같은 이름은 다시 등록할 수 있다 (@SQLRestriction)")
    void createItem_sameNameAsDeletedItem() {
        companyItemService.createItem(company.getId(), masterId, MASTER, request(ITEM_NAME, PRICE, null));
        CompanyItem first = companyItemRepository.findAll().get(0);
        first.delete(AUDITOR);
        companyItemRepository.saveAndFlush(first);

        companyItemService.createItem(company.getId(), masterId, MASTER, request(ITEM_NAME, PRICE, null));

        assertThat(companyItemRepository.count()).isEqualTo(1);         // 삭제되지 않은 상품
        assertThat(countAllItemRows()).isEqualTo(2);                     // 삭제된 행 포함 전체
    }

    @Test
    @DisplayName("삭제된 업체에는 상품을 등록할 수 없다 (404)")
    void createItem_deletedCompany() {
        company.delete(AUDITOR);
        companyRepository.saveAndFlush(company);

        assertThatThrownBy(() -> companyItemService.createItem(
                company.getId(), masterId, MASTER, request(ITEM_NAME, PRICE, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMPANY_NOT_FOUND);

        assertThat(countAllItemRows()).isZero();
    }

    // ===== 헬퍼 =====

    private Company saveCompany(String name) {
        return companyRepository.saveAndFlush(
                Company.create(UUID.randomUUID(), UUID.randomUUID(), name, "독도"));
    }

    private int countAllItemRows() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM company_schema.p_company_items", Integer.class);
        return count == null ? 0 : count;
    }

    private CompanyItemCreateRequest request(String name, Long price, String description) {
        CompanyItemCreateRequest request = mock(CompanyItemCreateRequest.class);
        given(request.getName()).willReturn(name);
        given(request.getPrice()).willReturn(price);
        given(request.getDescription()).willReturn(description);
        return request;
    }
}
package logistics.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import logistics.company.application.service.CompanyItemService;
import logistics.company.domain.entity.Company;
import logistics.company.domain.entity.CompanyItem;
import logistics.company.infrastructure.client.HubClient;
import logistics.company.infrastructure.client.UserClient;
import logistics.company.infrastructure.repository.CompanyItemRepository;
import logistics.company.infrastructure.repository.CompanyItemSpecification;
import logistics.company.infrastructure.repository.CompanyRepository;
import logistics.company.presentation.dto.request.companyItem.CompanyItemSearchCondition;
import logistics.company.presentation.dto.response.CompanyItemResponse;
import logistics.company.presentation.exception.BusinessException;
import logistics.company.presentation.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
class CompanyItemSearchIntegrationTest {

    private static final Pageable PAGEABLE = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));

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

    private Company seafood;       // 스파르타수산
    private Company dried;         // 한국건어물
    private CompanyItem squid;     // 마른오징어 15000, 국산 오징어
    private CompanyItem anchovy;   // 마른멸치 8000, 국산 멸치

    @BeforeEach
    void setUp() {
        given(auditorAware.getCurrentAuditor()).willReturn(Optional.of("tester"));

        seafood = saveCompany("스파르타수산");
        dried = saveCompany("한국건어물");

        squid = saveItem(seafood, "마른오징어", 15000L, "국산 오징어");
        saveItem(seafood, "Dried Squid", 30000L, "export");
        anchovy = saveItem(dried, "마른멸치", 8000L, "국산 멸치");

        // 삭제된 상품: 모든 검색에서 제외되어야 함
        CompanyItem deleted = saveItem(dried, "삭제된오징어", 1000L, "국산");
        deleted.delete("tester");
        companyItemRepository.saveAndFlush(deleted);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM company_schema.p_company_items");
        jdbcTemplate.update("DELETE FROM company_schema.p_companies");
    }

    // ===== 조건별 검색 =====

    @Test
    @DisplayName("조건 없음: 삭제된 상품을 제외한 전체 (빈 문자열·공백도 조건 없음)")
    void search_noCondition() {
        assertThat(names(cond())).containsExactlyInAnyOrder("마른오징어", "Dried Squid", "마른멸치");

        CompanyItemSearchCondition blank = cond();
        blank.setItemName("   ");
        blank.setCompanyName("");
        assertThat(names(blank)).hasSize(3);
    }

    @Test
    @DisplayName("상품명: 부분 일치, 삭제된 상품 제외")
    void search_itemName() {
        CompanyItemSearchCondition c = cond();
        c.setItemName("오징어");

        assertThat(names(c)).containsExactly("마른오징어");
    }

    @Test
    @DisplayName("상품명: 대소문자 구분 안 함")
    void search_itemName_caseInsensitive() {
        CompanyItemSearchCondition c = cond();
        c.setItemName("SQUID");

        assertThat(names(c)).containsExactly("Dried Squid");
    }

    @Test
    @DisplayName("업체 ID: 해당 업체 상품만 (업체 내 상품 조회)")
    void search_companyId() {
        CompanyItemSearchCondition c = cond();
        c.setCompanyId(seafood.getId());

        assertThat(names(c)).containsExactlyInAnyOrder("마른오징어", "Dried Squid");
    }

    @Test
    @DisplayName("업체명: 업체 테이블 조인으로 부분 일치")
    void search_companyName() {
        CompanyItemSearchCondition c = cond();
        c.setCompanyName("건어");

        assertThat(names(c)).containsExactly("마른멸치");
    }

    @Test
    @DisplayName("상품 설명: 부분 일치")
    void search_description() {
        CompanyItemSearchCondition c = cond();
        c.setDescription("국산");

        assertThat(names(c)).containsExactlyInAnyOrder("마른오징어", "마른멸치");
    }

    @Test
    @DisplayName("상품 ID: 정확히 일치")
    void search_itemId() {
        CompanyItemSearchCondition c = cond();
        c.setItemId(squid.getId());

        assertThat(names(c)).containsExactly("마른오징어");
    }

    @Test
    @DisplayName("가격 범위: 경계값 포함 (8000 이상 15000 이하)")
    void search_priceRange_inclusive() {
        CompanyItemSearchCondition c = cond();
        c.setMinPrice(8000L);
        c.setMaxPrice(15000L);

        assertThat(names(c)).containsExactlyInAnyOrder("마른오징어", "마른멸치");
    }

    @Test
    @DisplayName("여러 조건은 AND로 결합")
    void search_and() {
        CompanyItemSearchCondition match = cond();
        match.setCompanyName("수산");
        match.setItemName("오징어");
        assertThat(names(match)).containsExactly("마른오징어");

        CompanyItemSearchCondition noMatch = cond();
        noMatch.setCompanyName("건어");
        noMatch.setItemName("오징어");
        assertThat(names(noMatch)).isEmpty();
    }

    // ===== 정렬 / 조회 방식 =====

    @Test
    @DisplayName("없는 정렬 필드는 PropertyReferenceException (GlobalExceptionHandler에서 400)")
    void search_invalidSortProperty() {
        Pageable invalidSort = PageRequest.of(0, 20, Sort.by("abc"));

        assertThatThrownBy(() -> companyItemService.searchItems(cond(), invalidSort))
                .isInstanceOf(PropertyReferenceException.class);
    }

    @Test
    @DisplayName("@EntityGraph: 트랜잭션 밖에서도 업체명을 꺼낼 수 있다 (업체를 함께 조회)")
    void entityGraph_companyFetched() {
        Page<CompanyItem> page = companyItemRepository.findAll(
                CompanyItemSpecification.search(null, null, null, null, null, null, null), PAGEABLE);
        CompanyItem found = companyItemRepository.findById(squid.getId()).orElseThrow();

        // 지연 로딩이었다면 트랜잭션 밖이라 LazyInitializationException 발생
        assertThat(page.getContent()).allSatisfy(item -> assertThat(item.getCompany().getName()).isNotBlank());
        assertThat(found.getCompany().getName()).isEqualTo("스파르타수산");
    }

    // ===== 상세 조회 =====

    @Test
    @DisplayName("상세 조회: 삭제된 상품은 404")
    void getItem_deleted() {
        CompanyItem deleted = saveItem(seafood, "곧삭제", 1000L, null);
        deleted.delete("tester");
        companyItemRepository.saveAndFlush(deleted);

        assertThatThrownBy(() -> companyItemService.getItem(deleted.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ITEM_NOT_FOUND);
    }

    // ===== 헬퍼 =====

    private List<String> names(CompanyItemSearchCondition condition) {
        return companyItemService.searchItems(condition, PAGEABLE)
                .getContent().stream().map(CompanyItemResponse::getName).toList();
    }

    private CompanyItemSearchCondition cond() {
        return new CompanyItemSearchCondition();
    }

    private Company saveCompany(String name) {
        return companyRepository.saveAndFlush(
                Company.create(UUID.randomUUID(), UUID.randomUUID(), name, "서울 서초구 중앙대로3"));
    }

    private CompanyItem saveItem(Company company, String name, Long price, String description) {
        return companyItemRepository.saveAndFlush(CompanyItem.create(company, name, price, description));
    }
}
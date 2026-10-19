package logistics.company;

import logistics.company.application.service.CompanyItemService;
import logistics.company.domain.entity.Company;
import logistics.company.domain.entity.CompanyItem;
import logistics.company.infrastructure.repository.CompanyItemRepository;
import logistics.company.infrastructure.repository.CompanyRepository;
import logistics.company.presentation.dto.request.companyItem.CompanyItemCreateRequest;
import logistics.company.presentation.dto.response.CompanyItemResponse;
import logistics.company.presentation.exception.BusinessException;
import logistics.company.presentation.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyItemServiceTest {

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID HUB_ID = UUID.randomUUID();
    private static final UUID MANAGER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();
    private static final UUID MASTER_ID = UUID.randomUUID();

    private static final String COMPANY_NAME = "스파르타수산";
    private static final String ITEM_NAME = "마른오징어";
    private static final Long PRICE = 15000L;
    private static final String DESCRIPTION = "국산 마른오징어 10마리";

    private static final String MASTER = "MASTER";
    private static final String USER = "USER";

    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private CompanyItemRepository companyItemRepository;

    @InjectMocks
    private CompanyItemService companyItemService;

    private Company company;

    @BeforeEach
    void setUp() {
        company = Company.create(HUB_ID, MANAGER_ID, COMPANY_NAME, "독도");
    }

    // ===== 성공 =====

    @Test
    @DisplayName("MASTER: 상품을 등록하고, 요청 값이 그대로 저장된다")
    void createItem_master_success() {
        givenCompanyExists();
        givenSaveReturnsArgument();

        CompanyItemResponse response = companyItemService.createItem(
                COMPANY_ID, MASTER_ID, MASTER, request(ITEM_NAME, PRICE, DESCRIPTION));

        CompanyItem saved = captureSavedItem();
        assertThat(saved.getCompany()).isSameAs(company);
        assertThat(saved.getName()).isEqualTo(ITEM_NAME);
        assertThat(saved.getPrice()).isEqualTo(PRICE);
        assertThat(saved.getDescription()).isEqualTo(DESCRIPTION);

        assertThat(response.getName()).isEqualTo(ITEM_NAME);
        assertThat(response.getCompanyName()).isEqualTo(COMPANY_NAME);
    }

    @Test
    @DisplayName("업체 관리자 본인: 자기 업체에 상품을 등록할 수 있다")
    void createItem_manager_success() {
        givenCompanyExists();
        givenSaveReturnsArgument();

        companyItemService.createItem(COMPANY_ID, MANAGER_ID, USER, request(ITEM_NAME, PRICE, DESCRIPTION));

        then(companyItemRepository).should().saveAndFlush(any(CompanyItem.class));
    }

    // ===== 상품명 규칙 =====

    @Test
    @DisplayName("상품명 앞뒤 공백을 제거한 값으로 중복 확인하고 저장한다")
    void createItem_nameTrimmed() {
        givenCompanyExists();
        givenSaveReturnsArgument();

        companyItemService.createItem(COMPANY_ID, MASTER_ID, MASTER, request("  " + ITEM_NAME + "  ", PRICE, null));

        then(companyItemRepository).should().existsByCompanyIdAndName(COMPANY_ID, ITEM_NAME);
        assertThat(captureSavedItem().getName()).isEqualTo(ITEM_NAME);
    }

    @Test
    @DisplayName("상품명은 대소문자를 구분한다 (소문자로 바꾸지 않고 그대로 확인)")
    void createItem_nameCaseSensitive() {
        givenCompanyExists();
        givenSaveReturnsArgument();

        companyItemService.createItem(COMPANY_ID, MASTER_ID, MASTER, request("Squid", PRICE, null));

        then(companyItemRepository).should().existsByCompanyIdAndName(COMPANY_ID, "Squid");
        assertThat(captureSavedItem().getName()).isEqualTo("Squid");
    }

    // ===== 실패 =====

    @Test
    @DisplayName("업체가 없거나 삭제됐으면 404, 상품 저장소는 사용하지 않는다")
    void createItem_companyNotFound() {
        given(companyRepository.findById(COMPANY_ID)).willReturn(Optional.empty());

        assertBusinessException(
                () -> companyItemService.createItem(COMPANY_ID, MASTER_ID, MASTER, request(ITEM_NAME, PRICE, null)),
                ErrorCode.COMPANY_NOT_FOUND);

        verifyNoInteractions(companyItemRepository);
    }

    @Test
    @DisplayName("관리자도 MASTER도 아니면 403, 중복 확인·저장을 하지 않는다")
    void createItem_forbidden() {
        givenCompanyExists();

        assertBusinessException(
                () -> companyItemService.createItem(COMPANY_ID, OTHER_USER_ID, USER, request(ITEM_NAME, PRICE, null)),
                ErrorCode.ITEM_FORBIDDEN);

        verifyNoInteractions(companyItemRepository);
    }

    @Test
    @DisplayName("같은 업체에 같은 상품명이 있으면 409, 저장하지 않는다")
    void createItem_duplicateName() {
        givenCompanyExists();
        given(companyItemRepository.existsByCompanyIdAndName(COMPANY_ID, ITEM_NAME)).willReturn(true);

        assertBusinessException(
                () -> companyItemService.createItem(COMPANY_ID, MASTER_ID, MASTER, request(ITEM_NAME, PRICE, null)),
                ErrorCode.ITEM_NAME_ALREADY_EXISTS);

        then(companyItemRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("동시 요청으로 인덱스 위반이 나면 예외가 전파된다 (GlobalExceptionHandler에서 409)")
    void createItem_uniqueIndexViolation() {
        givenCompanyExists();
        given(companyItemRepository.saveAndFlush(any(CompanyItem.class)))
                .willThrow(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> companyItemService.createItem(
                COMPANY_ID, MASTER_ID, MASTER, request(ITEM_NAME, PRICE, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ===== 헬퍼 =====

    private void givenCompanyExists() {
        given(companyRepository.findById(COMPANY_ID)).willReturn(Optional.of(company));
    }

    // saveAndFlush에 넘긴 엔티티를 그대로 돌려줌 (실제 저장처럼 동작)
    private void givenSaveReturnsArgument() {
        willAnswer(invocation -> invocation.getArgument(0))
                .given(companyItemRepository).saveAndFlush(any(CompanyItem.class));
    }

    private CompanyItem captureSavedItem() {
        ArgumentCaptor<CompanyItem> captor = ArgumentCaptor.forClass(CompanyItem.class);
        then(companyItemRepository).should().saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private CompanyItemCreateRequest request(String name, Long price, String description) {
        CompanyItemCreateRequest request =
                mock(CompanyItemCreateRequest.class, withSettings().strictness(Strictness.LENIENT));
        given(request.getName()).willReturn(name);
        given(request.getPrice()).willReturn(price);
        given(request.getDescription()).willReturn(description);
        return request;
    }

    private void assertBusinessException(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }
}
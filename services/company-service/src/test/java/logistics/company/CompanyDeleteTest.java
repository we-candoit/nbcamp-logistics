package logistics.company;

import logistics.company.application.service.CompanyService;
import logistics.company.domain.entity.Company;
import logistics.company.infrastructure.repository.CompanyItemRepository;
import logistics.company.infrastructure.repository.CompanyRepository;
import logistics.company.presentation.exception.BusinessException;
import logistics.company.presentation.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CompanyDeleteTest {

    @InjectMocks
    private CompanyService companyService;

    @Mock private CompanyRepository companyRepository;
    @Mock private CompanyItemRepository companyItemRepository;

    private static final UUID MANAGER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();
    private static final UUID MASTER_ID = UUID.randomUUID();

    private Company company;

    @BeforeEach
    void setUp() {
        company = Company.create(UUID.randomUUID(), MANAGER_ID, "스파르타수산", "서울특별시 강남구 테헤란로 152");
        ReflectionTestUtils.setField(company, "id", UUID.randomUUID());
    }

    private void assertErrorCode(Throwable thrown, ErrorCode expected) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expected);
    }

    @Test
    @DisplayName("업체 관리자는 자기 업체를 삭제할 수 있고, 소속 상품도 같은 삭제 시각으로 함께 삭제된다")
    void deleteCompany_byManager() {
        given(companyRepository.findById(company.getId())).willReturn(Optional.of(company));

        companyService.deleteCompany(company.getId(), MANAGER_ID, "USER");

        assertThat(company.getDeletedAt()).isNotNull();
        assertThat(company.getDeletedBy()).isEqualTo(MANAGER_ID);
        then(companyItemRepository).should()
                .softDeleteAllByCompanyId(company.getId(), MANAGER_ID, company.getDeletedAt());
    }

    @Test
    @DisplayName("MASTER는 다른 업체도 삭제할 수 있다")
    void deleteCompany_byMaster() {
        given(companyRepository.findById(company.getId())).willReturn(Optional.of(company));

        companyService.deleteCompany(company.getId(), MASTER_ID, "MASTER");

        assertThat(company.getDeletedBy()).isEqualTo(MASTER_ID);
        then(companyItemRepository).should()
                .softDeleteAllByCompanyId(company.getId(), MASTER_ID, company.getDeletedAt());
    }

    @Test
    @DisplayName("업체가 없거나 이미 삭제됐으면 COMPANY_NOT_FOUND, 상품 삭제도 실행되지 않는다")
    void deleteCompany_notFound() {
        given(companyRepository.findById(company.getId())).willReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> companyService.deleteCompany(company.getId(), MANAGER_ID, "USER"));

        assertErrorCode(thrown, ErrorCode.COMPANY_NOT_FOUND);
        then(companyItemRepository).should(never()).softDeleteAllByCompanyId(any(), any(), any());
    }

    @Test
    @DisplayName("다른 업체 사용자는 COMPANY_FORBIDDEN, 업체·상품 모두 삭제되지 않는다")
    void deleteCompany_forbidden() {
        given(companyRepository.findById(company.getId())).willReturn(Optional.of(company));

        Throwable thrown = catchThrowable(() -> companyService.deleteCompany(company.getId(), OTHER_USER_ID, "USER"));

        assertErrorCode(thrown, ErrorCode.COMPANY_FORBIDDEN);
        assertThat(company.getDeletedAt()).isNull();
        then(companyItemRepository).should(never()).softDeleteAllByCompanyId(any(), any(), any());
    }
}
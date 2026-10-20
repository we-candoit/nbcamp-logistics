package logistics.company;

import logistics.company.application.service.CompanyItemService;
import logistics.company.domain.entity.Company;
import logistics.company.domain.entity.CompanyItem;
import logistics.company.infrastructure.repository.CompanyItemRepository;
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
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CompanyItemDeleteTest {

    @InjectMocks
    private CompanyItemService companyItemService;

    @Mock
    private CompanyItemRepository companyItemRepository;

    private static final UUID MANAGER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();
    private static final UUID MASTER_ID = UUID.randomUUID();

    private CompanyItem item;

    @BeforeEach
    void setUp() {
        // ※ 엔티티 생성 메서드는 프로젝트 코드에 맞게 수정
        Company company = Company.create(UUID.randomUUID(), MANAGER_ID, "스파르타수산", "서울특별시 강남구 테헤란로 152");
        ReflectionTestUtils.setField(company, "id", UUID.randomUUID());

        item = CompanyItem.create(company, "마른오징어", 15000L, "국산");
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
    }

    private void assertErrorCode(Throwable thrown, ErrorCode expected) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expected);
    }

    @Test
    @DisplayName("업체 관리자는 자기 업체 상품을 삭제할 수 있다 (deletedAt, deletedBy 기록)")
    void deleteItem_byManager() {
        given(companyItemRepository.findById(item.getId())).willReturn(Optional.of(item));

        companyItemService.deleteItem(item.getId(), MANAGER_ID, "USER");

        assertThat(item.getDeletedAt()).isNotNull();
        assertThat(item.getDeletedBy()).isEqualTo(MANAGER_ID);
    }

    @Test
    @DisplayName("MASTER는 다른 업체 상품도 삭제할 수 있다")
    void deleteItem_byMaster() {
        given(companyItemRepository.findById(item.getId())).willReturn(Optional.of(item));

        companyItemService.deleteItem(item.getId(), MASTER_ID, "MASTER");

        assertThat(item.getDeletedAt()).isNotNull();
        assertThat(item.getDeletedBy()).isEqualTo(MASTER_ID);
    }

    @Test
    @DisplayName("상품이 없거나 이미 삭제됐으면 ITEM_NOT_FOUND")
    void deleteItem_notFound() {
        given(companyItemRepository.findById(item.getId())).willReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> companyItemService.deleteItem(item.getId(), MANAGER_ID, "USER"));

        assertErrorCode(thrown, ErrorCode.ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 업체 사용자는 ITEM_FORBIDDEN, 삭제되지 않는다")
    void deleteItem_forbidden() {
        given(companyItemRepository.findById(item.getId())).willReturn(Optional.of(item));

        Throwable thrown = catchThrowable(() -> companyItemService.deleteItem(item.getId(), OTHER_USER_ID, "USER"));

        assertErrorCode(thrown, ErrorCode.ITEM_FORBIDDEN);
        assertThat(item.getDeletedAt()).isNull();
    }
}
package logistics.company;

import logistics.company.application.service.CompanyItemService;
import logistics.company.domain.entity.Company;
import logistics.company.domain.entity.CompanyItem;
import logistics.company.infrastructure.repository.CompanyItemRepository;
import logistics.company.presentation.dto.request.companyItem.CompanyItemUpdateRequest;
import logistics.company.presentation.dto.response.CompanyItemResponse;
import logistics.company.presentation.exception.BusinessException;
import logistics.company.presentation.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyItemUpdateTest {

    @InjectMocks
    private CompanyItemService companyItemService;

    @Mock
    private CompanyItemRepository companyItemRepository;

    private static final UUID MANAGER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();
    private static final UUID MASTER_ID = UUID.randomUUID();

    private Company company;
    private CompanyItem item;

    @BeforeEach
    void setUp() {
        // ※ 엔티티 생성 메서드 이름·파라미터는 프로젝트 코드에 맞게 수정
        company = Company.create(UUID.randomUUID(), MANAGER_ID, "스파르타수산", "서울특별시 강남구 테헤란로 152");
        ReflectionTestUtils.setField(company, "id", UUID.randomUUID());

        item = CompanyItem.create(company, "마른오징어", 15000L, "국산");
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
    }

    private CompanyItemUpdateRequest request(String name, Long price, String description) {
        // protected 기본 생성자 → BeanUtils가 접근 권한을 열고 생성
        CompanyItemUpdateRequest request = BeanUtils.instantiateClass(CompanyItemUpdateRequest.class);
        ReflectionTestUtils.setField(request, "name", name);
        ReflectionTestUtils.setField(request, "price", price);
        ReflectionTestUtils.setField(request, "description", description);
        return request;
    }

    private void givenItemExists() {
        given(companyItemRepository.findById(item.getId())).willReturn(Optional.of(item));
    }

    private void assertErrorCode(Throwable thrown, ErrorCode expected) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expected);
    }

    @Test
    @DisplayName("업체 관리자는 상품명·가격·설명을 수정할 수 있다")
    void updateItem_byManager() {
        givenItemExists();
        given(companyItemRepository.existsByCompanyIdAndName(company.getId(), "건오징어")).willReturn(false);

        CompanyItemResponse response = companyItemService.updateItem(
                item.getId(), MANAGER_ID, "USER", request("건오징어", 20000L, "수입"));

        assertThat(item.getName()).isEqualTo("건오징어");
        assertThat(item.getPrice()).isEqualTo(20000L);
        assertThat(item.getDescription()).isEqualTo("수입");
        then(companyItemRepository).should().flush();
    }

    @Test
    @DisplayName("MASTER는 다른 업체의 상품도 수정할 수 있다")
    void updateItem_byMaster() {
        givenItemExists();

        companyItemService.updateItem(item.getId(), MASTER_ID, "MASTER", request(null, 20000L, null));

        assertThat(item.getPrice()).isEqualTo(20000);
    }

    @Test
    @DisplayName("null 필드는 변경하지 않고, 상품명이 안 바뀌면 중복 확인도 하지 않는다")
    void updateItem_onlyPrice() {
        givenItemExists();

        companyItemService.updateItem(item.getId(), MANAGER_ID, "USER", request(null, 20000L, null));

        assertThat(item.getName()).isEqualTo("마른오징어");
        assertThat(item.getDescription()).isEqualTo("국산");
        then(companyItemRepository).should(never()).existsByCompanyIdAndName(any(), any());
    }

    @Test
    @DisplayName("상품명은 앞뒤 공백을 제거한 값으로 중복 확인하고 저장한다")
    void updateItem_trimName() {
        givenItemExists();
        given(companyItemRepository.existsByCompanyIdAndName(company.getId(), "건오징어")).willReturn(false);

        companyItemService.updateItem(item.getId(), MANAGER_ID, "USER", request("  건오징어  ", null, null));

        assertThat(item.getName()).isEqualTo("건오징어");
        then(companyItemRepository).should().existsByCompanyIdAndName(company.getId(), "건오징어");
    }

    @Test
    @DisplayName("상품이 없으면 ITEM_NOT_FOUND")
    void updateItem_notFound() {
        given(companyItemRepository.findById(item.getId())).willReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> companyItemService.updateItem(
                item.getId(), MANAGER_ID, "USER", request("건오징어", null, null)));

        assertErrorCode(thrown, ErrorCode.ITEM_NOT_FOUND);
        then(companyItemRepository).should(never()).flush();
    }

    @Test
    @DisplayName("다른 업체 사용자는 ITEM_FORBIDDEN")
    void updateItem_forbidden() {
        givenItemExists();

        Throwable thrown = catchThrowable(() -> companyItemService.updateItem(
                item.getId(), OTHER_USER_ID, "USER", request("건오징어", null, null)));

        assertErrorCode(thrown, ErrorCode.ITEM_FORBIDDEN);
        assertThat(item.getName()).isEqualTo("마른오징어");
    }

    @Test
    @DisplayName("같은 업체에 같은 상품명이 있으면 ITEM_NAME_ALREADY_EXISTS")
    void updateItem_duplicateName() {
        givenItemExists();
        given(companyItemRepository.existsByCompanyIdAndName(company.getId(), "마른멸치")).willReturn(true);

        Throwable thrown = catchThrowable(() -> companyItemService.updateItem(
                item.getId(), MANAGER_ID, "USER", request("마른멸치", 20000L, null)));

        assertErrorCode(thrown, ErrorCode.ITEM_NAME_ALREADY_EXISTS);
        assertThat(item.getName()).isEqualTo("마른오징어");
        assertThat(item.getPrice()).isEqualTo(15000);   // 중복이면 다른 필드도 바뀌지 않음
        then(companyItemRepository).should(never()).flush();
    }

    @Test
    @DisplayName("모든 필드가 null이면 NO_UPDATE_FIELDS")
    void updateItem_allNull() {
        givenItemExists();

        Throwable thrown = catchThrowable(() -> companyItemService.updateItem(
                item.getId(), MANAGER_ID, "USER", request(null, null, null)));

        assertErrorCode(thrown, ErrorCode.NO_UPDATE_FIELDS);
    }

    @Test
    @DisplayName("공백만 다른 기존과 같은 이름이면 변경 없음으로 NO_UPDATE_FIELDS")
    void updateItem_sameNameAfterTrim() {
        givenItemExists();

        Throwable thrown = catchThrowable(() -> companyItemService.updateItem(
                item.getId(), MANAGER_ID, "USER", request(" 마른오징어 ", 15000L, "국산")));

        assertErrorCode(thrown, ErrorCode.NO_UPDATE_FIELDS);
        then(companyItemRepository).should(never()).existsByCompanyIdAndName(any(), any());
    }
}
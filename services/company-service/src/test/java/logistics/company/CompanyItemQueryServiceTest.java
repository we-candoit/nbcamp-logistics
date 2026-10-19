package logistics.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import logistics.company.application.service.CompanyItemService;
import logistics.company.domain.entity.Company;
import logistics.company.domain.entity.CompanyItem;
import logistics.company.infrastructure.repository.CompanyItemRepository;
import logistics.company.infrastructure.repository.CompanyRepository;
import logistics.company.presentation.dto.request.companyItem.CompanyItemSearchCondition;
import logistics.company.presentation.dto.response.CompanyItemResponse;
import logistics.company.presentation.exception.BusinessException;
import logistics.company.presentation.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class CompanyItemQueryServiceTest {

    private static final UUID ITEM_ID = UUID.randomUUID();
    private static final Pageable PAGEABLE = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));

    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private CompanyItemRepository companyItemRepository;

    @InjectMocks
    private CompanyItemService companyItemService;

    // ===== 상세 조회 =====

    @Test
    @DisplayName("상세 조회 성공: 상품과 소속 업체 정보를 응답한다")
    void getItem_success() {
        CompanyItem item = newItem("마른오징어", 15000L);
        given(companyItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));

        CompanyItemResponse response = companyItemService.getItem(ITEM_ID);

        assertThat(response.getName()).isEqualTo("마른오징어");
        assertThat(response.getPrice()).isEqualTo(15000L);
        assertThat(response.getCompanyName()).isEqualTo("스파르타수산");
    }

    @Test
    @DisplayName("상세 조회 실패: 상품이 없거나 삭제됐으면 404 ITEM_NOT_FOUND")
    void getItem_notFound() {
        given(companyItemRepository.findById(ITEM_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> companyItemService.getItem(ITEM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ITEM_NOT_FOUND);
    }

    // ===== 목록 검색 =====

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("목록 검색: 조회 결과를 CompanyItemResponse 페이지로 변환한다")
    void searchItems_success() {
        given(companyItemRepository.findAll(any(Specification.class), eq(PAGEABLE)))
                .willReturn(new PageImpl<>(List.of(newItem("마른오징어", 15000L)), PAGEABLE, 1));

        Page<CompanyItemResponse> result = companyItemService.searchItems(new CompanyItemSearchCondition(), PAGEABLE);

        assertThat(result.getContent()).extracting(CompanyItemResponse::getName).containsExactly("마른오징어");
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("목록 검색: 최소 가격 > 최대 가격이면 400, 조회하지 않는다")
    void searchItems_invalidPriceRange() {
        CompanyItemSearchCondition condition = new CompanyItemSearchCondition();
        condition.setMinPrice(5000L);
        condition.setMaxPrice(1000L);

        assertThatThrownBy(() -> companyItemService.searchItems(condition, PAGEABLE))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BAD_REQUEST);

        verifyNoInteractions(companyItemRepository);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("목록 검색: 최소 가격 = 최대 가격은 허용한다 (경계값 포함)")
    void searchItems_samePriceAllowed() {
        given(companyItemRepository.findAll(any(Specification.class), eq(PAGEABLE)))
                .willReturn(Page.empty(PAGEABLE));

        CompanyItemSearchCondition condition = new CompanyItemSearchCondition();
        condition.setMinPrice(1000L);
        condition.setMaxPrice(1000L);

        assertThat(companyItemService.searchItems(condition, PAGEABLE).getContent()).isEmpty();
    }

    // ===== 헬퍼 =====

    private CompanyItem newItem(String name, Long price) {
        Company company = Company.create(UUID.randomUUID(), UUID.randomUUID(), "스파르타수산", "서울 서초구 중앙대로3");
        return CompanyItem.create(company, name, price, null);
    }
}
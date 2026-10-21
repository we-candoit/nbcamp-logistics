package logistics.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.List;
import java.util.UUID;

import logistics.company.application.service.CompanyItemService;
import logistics.company.domain.entity.Company;
import logistics.company.domain.entity.CompanyItem;
import logistics.company.infrastructure.repository.CompanyItemRepository;
import logistics.company.infrastructure.repository.CompanyRepository;
import logistics.company.presentation.dto.response.internal.CompanyItemBulkResponse;
import logistics.company.presentation.dto.response.internal.CompanyItemInternalResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CompanyItemBulkQueryTest {

    private static final UUID ID_A = UUID.randomUUID();
    private static final UUID ID_B = UUID.randomUUID();
    private static final UUID ID_X = UUID.randomUUID();   // 존재하지 않는 ID

    private static final UUID HUB_ID = UUID.randomUUID();
    private static final UUID MANAGER_ID = UUID.randomUUID();
    private static final String ADDRESS = "서울 강남구 테헤란로 311";

    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private CompanyItemRepository companyItemRepository;

    @InjectMocks
    private CompanyItemService companyItemService;

    @Test
    @DisplayName("응답 필드: 상품 ID, 가격 + 소속 업체의 허브 ID, 주소, 관리자 ID")
    void getItems_fields() {
        given(companyItemRepository.findAllByIdIn(List.of(ID_A))).willReturn(List.of(item(ID_A, 15000L)));

        CompanyItemInternalResponse response = companyItemService.getItems(List.of(ID_A)).getItems().get(0);

        assertThat(response.getItemId()).isEqualTo(ID_A);
        assertThat(response.getPrice()).isEqualTo(15000L);
        assertThat(response.getHubId()).isEqualTo(HUB_ID);
        assertThat(response.getAddress()).isEqualTo(ADDRESS);
        assertThat(response.getManagerUserId()).isEqualTo(MANAGER_ID);
    }

    @Test
    @DisplayName("결과는 요청한 ID 순서대로 (DB 반환 순서와 무관)")
    void getItems_keepsRequestOrder() {
        given(companyItemRepository.findAllByIdIn(List.of(ID_B, ID_A)))
                .willReturn(List.of(item(ID_A, 1000L), item(ID_B, 2000L)));

        CompanyItemBulkResponse response = companyItemService.getItems(List.of(ID_B, ID_A));

        assertThat(response.getItems())
                .extracting(CompanyItemInternalResponse::getItemId)
                .containsExactly(ID_B, ID_A);
    }

    @Test
    @DisplayName("중복 ID는 한 번만 조회하고 한 번만 응답한다 (쿼리 1번)")
    void getItems_distinct() {
        given(companyItemRepository.findAllByIdIn(List.of(ID_A, ID_B)))
                .willReturn(List.of(item(ID_A, 1000L), item(ID_B, 2000L)));

        CompanyItemBulkResponse response = companyItemService.getItems(List.of(ID_A, ID_A, ID_B));

        assertThat(response.getItems()).hasSize(2);
        then(companyItemRepository).should().findAllByIdIn(List.of(ID_A, ID_B));
    }

    @Test
    @DisplayName("일부가 없으면 실패하지 않고 notFoundIds에 담는다")
    void getItems_partiallyNotFound() {
        given(companyItemRepository.findAllByIdIn(List.of(ID_A, ID_X))).willReturn(List.of(item(ID_A, 1000L)));

        CompanyItemBulkResponse response = companyItemService.getItems(List.of(ID_A, ID_X));

        assertThat(response.getItems()).extracting(CompanyItemInternalResponse::getItemId).containsExactly(ID_A);
        assertThat(response.getNotFoundIds()).containsExactly(ID_X);
    }

    @Test
    @DisplayName("전부 없으면 빈 목록 + 모든 ID가 notFoundIds")
    void getItems_allNotFound() {
        given(companyItemRepository.findAllByIdIn(List.of(ID_X))).willReturn(List.of());

        CompanyItemBulkResponse response = companyItemService.getItems(List.of(ID_X));

        assertThat(response.getItems()).isEmpty();
        assertThat(response.getNotFoundIds()).containsExactly(ID_X);
    }

    private CompanyItem item(UUID id, Long price) {
        Company company = Company.create(HUB_ID, MANAGER_ID, "스파르타수산", ADDRESS);
        CompanyItem item = CompanyItem.create(company, "상품-" + price, price, null);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }
}
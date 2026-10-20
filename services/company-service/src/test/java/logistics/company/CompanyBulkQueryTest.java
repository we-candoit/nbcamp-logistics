package logistics.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.List;
import java.util.UUID;

import logistics.company.application.service.CompanyService;
import logistics.company.domain.entity.Company;
import logistics.company.infrastructure.client.HubClient;
import logistics.company.infrastructure.client.UserClient;
import logistics.company.infrastructure.repository.CompanyRepository;
import logistics.company.presentation.dto.response.internal.CompanyBulkResponse;
import logistics.company.presentation.dto.response.internal.CompanyInternalResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CompanyBulkQueryTest {

    private static final UUID ID_A = UUID.randomUUID();
    private static final UUID ID_B = UUID.randomUUID();
    private static final UUID ID_X = UUID.randomUUID();   // 존재하지 않는 ID

    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private HubClient hubClient;
    @Mock
    private UserClient userClient;

    @InjectMocks
    private CompanyService companyService;

    @Test
    @DisplayName("응답 필드: 업체 ID, 허브 ID, 관리자 ID, 업체명, 주소")
    void getCompanies_fields() {
        Company a = company(ID_A, "스파르타수산");
        given(companyRepository.findAllById(List.of(ID_A))).willReturn(List.of(a));

        CompanyInternalResponse response = companyService.getCompanies(List.of(ID_A)).getCompanies().get(0);

        assertThat(response.getCompanyId()).isEqualTo(ID_A);
        assertThat(response.getHubId()).isEqualTo(a.getHubId());
        assertThat(response.getManagerUserId()).isEqualTo(a.getManagerUserId());
        assertThat(response.getName()).isEqualTo("스파르타수산");
        assertThat(response.getAddress()).isEqualTo(a.getAddress());
    }

    @Test
    @DisplayName("결과는 요청한 ID 순서대로 (DB 반환 순서와 무관)")
    void getCompanies_keepsRequestOrder() {
        given(companyRepository.findAllById(List.of(ID_B, ID_A)))
                .willReturn(List.of(company(ID_A, "A"), company(ID_B, "B")));   // DB는 다른 순서로 반환

        CompanyBulkResponse response = companyService.getCompanies(List.of(ID_B, ID_A));

        assertThat(response.getCompanies())
                .extracting(CompanyInternalResponse::getCompanyId)
                .containsExactly(ID_B, ID_A);
    }

    @Test
    @DisplayName("중복 ID는 한 번만 조회하고 한 번만 응답한다 (쿼리 1번)")
    void getCompanies_distinct() {
        given(companyRepository.findAllById(List.of(ID_A, ID_B)))
                .willReturn(List.of(company(ID_A, "A"), company(ID_B, "B")));

        CompanyBulkResponse response = companyService.getCompanies(List.of(ID_A, ID_A, ID_B));

        assertThat(response.getCompanies()).hasSize(2);
        then(companyRepository).should().findAllById(List.of(ID_A, ID_B));
    }

    @Test
    @DisplayName("일부가 없으면 실패하지 않고 notFoundIds에 담는다")
    void getCompanies_partiallyNotFound() {
        given(companyRepository.findAllById(List.of(ID_A, ID_X))).willReturn(List.of(company(ID_A, "A")));

        CompanyBulkResponse response = companyService.getCompanies(List.of(ID_A, ID_X));

        assertThat(response.getCompanies()).extracting(CompanyInternalResponse::getCompanyId).containsExactly(ID_A);
        assertThat(response.getNotFoundIds()).containsExactly(ID_X);
    }

    @Test
    @DisplayName("전부 없으면 빈 목록 + 모든 ID가 notFoundIds")
    void getCompanies_allNotFound() {
        given(companyRepository.findAllById(List.of(ID_X))).willReturn(List.of());

        CompanyBulkResponse response = companyService.getCompanies(List.of(ID_X));

        assertThat(response.getCompanies()).isEmpty();
        assertThat(response.getNotFoundIds()).containsExactly(ID_X);
    }

    private Company company(UUID id, String name) {
        Company company = Company.create(UUID.randomUUID(), UUID.randomUUID(), name, "서울 서초구 중앙대로3");
        ReflectionTestUtils.setField(company, "id", id);
        return company;
    }
}
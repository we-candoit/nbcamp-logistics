package logistics.company;

import jakarta.persistence.criteria.*;
import logistics.company.domain.entity.Company;
import logistics.company.infrastructure.repository.CompanySpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanySpecificationTest {

    private Root<Company> root;
    private CriteriaQuery<?> query;
    private CriteriaBuilder cb;
    private Expression<String> lowerExpression;
    private Predicate predicate;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() {
        root = lenientMock(Root.class);
        query = lenientMock(CriteriaQuery.class);
        cb = lenientMock(CriteriaBuilder.class);
        lowerExpression = lenientMock(Expression.class);
        predicate = lenientMock(Predicate.class);

        Path path = lenientMock(Path.class);
        given(root.get(anyString())).willReturn(path);
        given(cb.lower(any())).willReturn(lowerExpression);
        given(cb.like(any(Expression.class), anyString())).willReturn(predicate);
    }

    @Test
    @DisplayName("업체명만 있으면: name 컬럼에만 LIKE 조건, 소문자·공백 제거 후 %검색어%")
    void search_nameOnly() {
        CompanySpecification.search("  SpaRta ", null).toPredicate(root, query, cb);

        then(root).should().get("name");
        then(root).should(never()).get("address");
        then(cb).should().like(lowerExpression, "%sparta%");
        then(cb).should().and(predicate);
    }

    @Test
    @DisplayName("주소만 있으면: address 컬럼에만 LIKE 조건")
    void search_addressOnly() {
        CompanySpecification.search(null, "부산").toPredicate(root, query, cb);

        then(root).should(never()).get("name");
        then(root).should().get("address");
        then(cb).should().like(lowerExpression, "%부산%");
        then(cb).should().and(predicate);
    }

    @Test
    @DisplayName("둘 다 있으면: 두 조건을 AND로 결합")
    void search_both() {
        CompanySpecification.search("수산", "부산").toPredicate(root, query, cb);

        then(cb).should().like(lowerExpression, "%수산%");
        then(cb).should().like(lowerExpression, "%부산%");
        then(cb).should(times(1)).and(new Predicate[]{predicate, predicate});   // 가변 인자 메서드로 고정
    }

    @Test
    @DisplayName("빈 값·공백·null이면: 조건 없음 (전체 조회)")
    void search_blank() {
        CompanySpecification.search("   ", "").toPredicate(root, query, cb);
        CompanySpecification.search(null, null).toPredicate(root, query, cb);

        verifyNoInteractions(root);
        then(cb).should(never()).like(any(Expression.class), anyString());
        then(cb).should(times(2)).and();
    }

    private static <T> T lenientMock(Class<T> type) {
        return mock(type, withSettings().strictness(Strictness.LENIENT));
    }
}
package logistics.company;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;

import logistics.company.presentation.dto.request.companyItem.CompanyItemSearchCondition;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CompanyItemSearchConditionValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    @DisplayName("최소 가격이 음수면 검증 실패 (minPrice 필드)")
    void minPrice_negative() {
        CompanyItemSearchCondition condition = new CompanyItemSearchCondition();
        condition.setMinPrice(-1L);

        Set<ConstraintViolation<CompanyItemSearchCondition>> violations = validator.validate(condition);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("minPrice");
    }

    @Test
    @DisplayName("최대 가격이 음수면 검증 실패 (maxPrice 필드)")
    void maxPrice_negative() {
        CompanyItemSearchCondition condition = new CompanyItemSearchCondition();
        condition.setMaxPrice(-1L);

        Set<ConstraintViolation<CompanyItemSearchCondition>> violations = validator.validate(condition);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("maxPrice");
    }

    @Test
    @DisplayName("0은 허용한다 (경계값)")
    void price_zero_allowed() {
        CompanyItemSearchCondition condition = new CompanyItemSearchCondition();
        condition.setMinPrice(0L);
        condition.setMaxPrice(0L);

        assertThat(validator.validate(condition)).isEmpty();
    }

    @Test
    @DisplayName("가격 조건이 없으면(null) 검증 통과 (조건 없음)")
    void price_null_allowed() {
        assertThat(validator.validate(new CompanyItemSearchCondition())).isEmpty();
    }
}
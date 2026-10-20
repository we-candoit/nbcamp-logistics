package logistics.company;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.executable.ExecutableValidator;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import logistics.company.presentation.controller.InternalController;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InternalControllerValidationTest {

    private static ValidatorFactory factory;
    private static ExecutableValidator validator;
    private final InternalController controller = new InternalController(null, null);

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator().forExecutables();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"getCompanies", "getItems"})
    @DisplayName("ids가 없으면(null) 검증 실패")
    void ids_null(String methodName) throws Exception {
        assertThat(validate(methodName, null)).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"getCompanies", "getItems"})
    @DisplayName("ids가 비어 있으면 검증 실패")
    void ids_empty(String methodName) throws Exception {
        assertThat(validate(methodName, List.of())).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"getCompanies", "getItems"})
    @DisplayName("ids 100개는 허용 (경계값)")
    void ids_100_allowed(String methodName) throws Exception {
        assertThat(validate(methodName, ids(100))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"getCompanies", "getItems"})
    @DisplayName("ids 101개는 검증 실패")
    void ids_101_rejected(String methodName) throws Exception {
        assertThat(validate(methodName, ids(101))).hasSize(1);
    }

    private Set<ConstraintViolation<InternalController>> validate(String methodName, List<UUID> ids) throws Exception {
        Method method = InternalController.class.getMethod(methodName, List.class);
        return validator.validateParameters(controller, method, new Object[]{ids});
    }

    private List<UUID> ids(int count) {
        return Stream.generate(UUID::randomUUID).limit(count).toList();
    }
}
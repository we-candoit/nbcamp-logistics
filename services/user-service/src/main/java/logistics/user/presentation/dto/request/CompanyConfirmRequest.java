package logistics.user.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.UUID;

@Getter
public class CompanyConfirmRequest {

    @NotNull(message = "업체 ID는 필수입니다.")
    private UUID companyId;

    @NotBlank(message = "업체명은 필수입니다.")
    private String companyName;
}
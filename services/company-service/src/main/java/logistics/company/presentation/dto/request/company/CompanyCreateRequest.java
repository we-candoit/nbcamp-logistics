package logistics.company.presentation.dto.request.company;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class CompanyCreateRequest {

    @NotNull(message = "소속 허브 ID는 필수입니다.")
    private UUID hubId;                         // 소속 허브 id

    @NotNull(message = "업체 관리자 ID는 필수입니다.")
    private UUID managerUserId;                 // 업체 관리자 id

    @NotBlank(message = "업체명은 필수입니다.")
    @Size(max = 100, message = "업체명은 100자 이하여야 합니다.")
    private String name;                        // 업체명

    @NotBlank(message = "업체 주소는 필수입니다.")
    @Size(max = 255, message = "업체 주소는 255자 이하여야 합니다.")
    private String address;                     // 업체 주소
}

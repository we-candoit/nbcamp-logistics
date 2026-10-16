package logistics.company.presentation.dto.request.company;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class CompanyUpdateRequest {

    private UUID hubId;                         // 소속 허브 id (MASTER만 변경 가능)

    private UUID managerUserId;                 // 업체 관리자 id (MASTER만 변경 가능)

    @Pattern(regexp = ".*\\S.*", message = "업체명은 공백만 입력할 수 없습니다.")
    @Size(max = 100, message = "업체명은 100자 이하여야 합니다.")
    private String name;                        // 업체명

    @Pattern(regexp = ".*\\S.*", message = "업체 주소는 공백만 입력할 수 없습니다.")
    @Size(max = 255, message = "업체 주소는 255자 이하여야 합니다.")
    private String address;                     // 업체 주소
}
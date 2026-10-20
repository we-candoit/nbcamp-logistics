package logistics.user.presentation.dto.request;

import jakarta.validation.constraints.*;
import logistics.user.domain.enums.AffiliationType;
import lombok.Getter;

@Getter
public class JoinRequest {

    @NotBlank(message = "로그인 ID는 필수입니다.")
    @Size(min = 4, max = 10, message = "아이디는 4자 이상, 10자 이하로 입력해주세요.")
    @Pattern(regexp = "^[a-z0-9]+$", message = "소문자와 숫자만 사용할 수 있습니다.")
    private String loginId; // 로그인 ID

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(min = 8, max = 15, message = "비밀번호는 8자 이상, 15자 이하로 입력해주세요.")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).*$",
            message = "비밀번호는 알파벳 대소문자, 숫자, 특수문자를 각각 최소 1개 이상 포함해야 합니다."
    )
    private String password;

    @Size(min = 2, max = 10, message = "이름은 2자 이상, 10자 이하로 입력해주세요.")
    @NotBlank(message = "이름은 필수입니다.")
    private String name; // 회원 이름

    @Email(message = "이메일 형식이 맞지 않습니다.")
    @Size(max = 255)
    private String email; // 필수 X

    @NotBlank(message = "슬랙ID는 필수입니다.")
    @Size(max = 100, message = "슬랙ID는 100자 이하만 가능합니다.")
    private String slackId;

    @NotNull(message = "소속 유형[HUB or COMPANY]은 필수입니다.")
    private AffiliationType affiliationType;

    @NotBlank(message = "소속명(업체/허브)은 필수입니다.")
    @Size(max = 100, message = "소속명(업체/허브)은 100자 이하만 가능합니다.")
    private String affiliationName;
}
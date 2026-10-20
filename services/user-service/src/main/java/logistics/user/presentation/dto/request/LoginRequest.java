package logistics.user.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class LoginRequest {

    @NotBlank(message = "아이디를 입력해주세요.")
    private String loginId; // 로그인 ID

    @NotBlank(message = "비밀번호는 필수입니다.")
    private String password;
}
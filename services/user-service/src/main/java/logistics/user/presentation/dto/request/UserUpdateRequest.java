package logistics.user.presentation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class UserUpdateRequest {

    @Size(min = 2, max = 10, message = "이름은 2자 이상, 10자 이하로 입력해주세요.")
    private String name;

    @Email(message = "이메일 형식이 맞지 않습니다.")
    @Size(max = 255)
    private String email;
}
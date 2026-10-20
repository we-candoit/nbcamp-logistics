package logistics.order.infrastructure.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

//유저 서비스 공통 응답 / 실제 응답값은 data에 담겨서 옴
@Data
@AllArgsConstructor @NoArgsConstructor
public class UserApiResponse<T> {

    private boolean success;
    private String message;
    private T data;

}

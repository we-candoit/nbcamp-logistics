package logistics.company.infrastructure.client.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
}

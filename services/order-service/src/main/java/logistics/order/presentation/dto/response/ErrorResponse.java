package logistics.order.presentation.dto.response;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public final class ErrorResponse {

    private final int status;
    private final String message;
    private final LocalDateTime timestamp;

}

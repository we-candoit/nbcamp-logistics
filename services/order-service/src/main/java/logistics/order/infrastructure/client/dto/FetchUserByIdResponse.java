package logistics.order.infrastructure.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

//유저 상세조회 응답 / 유저 서비스는 실제 유저 정보를 data에 담아서 반환
@Data
@AllArgsConstructor @NoArgsConstructor
public class FetchUserByIdResponse {

    private boolean success;
    private String message;
    private UserData data;

    //이너클래스명을 Data로 하면 lombok @Data와 이름이 겹쳐 UserData 사용
    @Data
    @NoArgsConstructor
    public static class UserData {
        private UUID userId;
        private String loginId;
        private String name;
        private String email;
        private String slackId;
        private String role;
        //Boolean 래퍼 타입이라 lombok이 setIsDeliveryManager를 생성 → JSON 키 isDeliveryManager와 매핑
        private Boolean isDeliveryManager;
        private String affiliationType;
        private String affiliationName;
        private String affiliationStatus;
        private UUID hubId;
        private UUID companyId;
        private LocalDateTime createdAt;
    }
}

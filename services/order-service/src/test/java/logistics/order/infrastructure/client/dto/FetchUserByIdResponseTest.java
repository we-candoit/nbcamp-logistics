package logistics.order.infrastructure.client.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

//유저 서비스 상세조회 응답(JSON)이 DTO로 올바르게 변환되는지 확인
class FetchUserByIdResponseTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    public void 유저_상세조회_응답의_data가_UserData로_변환된다() {
        //given
        UUID userId = UUID.randomUUID();
        UUID hubId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        String json = """
                {
                  "success": true,
                  "message": null,
                  "data": {
                    "userId": "%s",
                    "loginId": "comp01",
                    "name": "강현우",
                    "email": "comp01@example.com",
                    "slackId": "U00COMP01",
                    "role": "USER",
                    "isDeliveryManager": false,
                    "affiliationType": "COMPANY",
                    "affiliationName": "일산건조식품가공",
                    "affiliationStatus": "CONFIRMED",
                    "hubId": "%s",
                    "companyId": "%s",
                    "createdAt": "2026-10-01T09:30:00"
                  }
                }
                """.formatted(userId, hubId, companyId);

        //when
        FetchUserByIdResponse response = jsonMapper.readValue(json, FetchUserByIdResponse.class);

        //then
        FetchUserByIdResponse.UserData data = response.getData();
        assertThat(response.isSuccess()).isTrue();
        assertThat(data.getUserId()).isEqualTo(userId);
        assertThat(data.getCompanyId()).isEqualTo(companyId);
        assertThat(data.getHubId()).isEqualTo(hubId);
        assertThat(data.getSlackId()).isEqualTo("U00COMP01");
        assertThat(data.getRole()).isEqualTo("USER");
        assertThat(data.getIsDeliveryManager()).isFalse();
        assertThat(data.getCreatedAt()).isNotNull();
    }
}

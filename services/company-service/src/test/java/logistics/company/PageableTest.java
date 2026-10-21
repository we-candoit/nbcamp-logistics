package logistics.company;

import logistics.company.infrastructure.client.HubClient;
import logistics.company.infrastructure.client.UserClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.AuditorAware;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@ActiveProfiles("test")
@SpringBootTest(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:company-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS company_schema",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.properties.hibernate.default_schema=company_schema",
                "spring.sql.init.mode=never",
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "server.port=0"
        }
)
@AutoConfigureMockMvc
public class PageableTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HubClient hubClient;
    @MockitoBean private UserClient userClient;
    @MockitoBean private AuditorAware<UUID> auditorAware;

    @Test
    @DisplayName("size가 최대값(100)을 넘으면 100으로 제한되고, 응답은 {content, page} 구조다")
    void maxPageSize() throws Exception {
        mockMvc.perform(get("/api/companies")
                        .param("size", "1000")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-Role-Name", "MASTER"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(jsonPath("$.page.size").value(100))
                .andExpect(jsonPath("$.content").isArray());
    }
}

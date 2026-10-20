package logistics.company.infrastructure.client;

import logistics.company.infrastructure.client.dto.HubResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "hub-service")
public interface HubClient {

    @GetMapping("/api/hubs/{hubId}")
    HubResponse getHub(@PathVariable("hubId") UUID hubId);  // 관리 허브 ID가 존재하는 허브인지 확인
}
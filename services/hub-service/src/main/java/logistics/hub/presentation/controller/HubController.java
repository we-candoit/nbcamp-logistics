package logistics.hub.presentation.controller;

import logistics.hub.presentation.dto.response.HubResponse;
import logistics.hub.application.service.HubService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/hubs")
public class HubController {

    private final HubService hubService;

    // 허브 목록 조회
    @GetMapping
    public List<HubResponse> getHubs() {
        return hubService.getHubs();
    }

    // 허브 상세 조회
    @GetMapping("/{hubId}")
    public HubResponse getHub(@PathVariable("hubId") UUID hubId) {
        return hubService.getHub(hubId);
    }
}
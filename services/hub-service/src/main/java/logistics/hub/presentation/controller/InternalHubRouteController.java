package logistics.hub.presentation.controller;

import logistics.hub.application.service.HubRouteService;
import logistics.hub.presentation.dto.response.HubRouteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/hubs/routes")
public class InternalHubRouteController {

    private final HubRouteService hubRouteService;

    // 출발 허브와 도착 허브간 이동 경로 조회
    @GetMapping("/hubs")
    public HubRouteResponse getRouteByHubs(
            @RequestParam UUID originHubId,
            @RequestParam UUID destinationHubId
    ) {
        return hubRouteService.getRouteByHubs(
                originHubId,
                destinationHubId
        );
    }
}
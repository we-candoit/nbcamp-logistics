package logistics.hub.presentation.controller;

import logistics.hub.application.service.HubRouteService;
import logistics.hub.presentation.dto.response.HubRouteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/hubs")
public class HubRouteController {

    private final HubRouteService hubRouteService;

    // 허브 이동정보 목록 조회
    @GetMapping("/routes")
    public List<HubRouteResponse> getHubRoutes() {
        return hubRouteService.getHubRoutes();
    }

    // 허브 이동정보 상세 조회
    @GetMapping("/route/{routeId}")
    public HubRouteResponse getHubRoute(@PathVariable("routeId") UUID routeId) {
        return hubRouteService.getHubRoute(routeId);
    }

    // 출발 허브와 도착 허브간 이동 경로 조회
    @GetMapping("/routes/by-hubs")
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
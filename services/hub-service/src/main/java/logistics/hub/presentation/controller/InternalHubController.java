package logistics.hub.presentation.controller;

import logistics.hub.application.service.HubService;
import logistics.hub.presentation.dto.response.HubCheckResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/hubs")
public class InternalHubController {

    private final HubService hubService;

    @GetMapping
    public HubCheckResponse getHubByName(
            @RequestParam("name") String hubName
    ) {
        return hubService.getHubByName(hubName);
    }
}

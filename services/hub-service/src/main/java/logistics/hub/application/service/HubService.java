package logistics.hub.application.service;

import logistics.hub.domain.entity.Hub;
import logistics.hub.infrastructure.repository.HubRepository;
import logistics.hub.presentation.dto.response.HubCheckResponse;
import logistics.hub.presentation.dto.response.HubResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HubService {

    private final HubRepository hubRepository;

    public List<HubResponse> getHubs() {
        List<Hub> hubs = hubRepository.findAllByDeletedAtIsNullOrderByNameAsc();

        return hubs.stream()
                .map(HubResponse::new)
                .toList();
    }

    public HubResponse getHub(UUID hubId) {
        Hub hub = hubRepository.findByIdAndDeletedAtIsNull(hubId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "해당 허브를 찾을 수 없습니다."
                ));

        return new HubResponse(hub);
    }

    public HubCheckResponse getHubByName(String hubName) {
        if (hubName == null || hubName.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "허브명은 필수입니다."
            );
        }

        String normalizedName = hubName.strip();

        if (normalizedName.length() > 50) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "허브명은 50자 이하여야 합니다."
            );
        }

        Hub hub = hubRepository
                .findByNameAndDeletedAtIsNull(normalizedName)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "해당 이름의 허브를 찾을 수 없습니다."
                        )
                );

        return new HubCheckResponse(hub);

    }

}
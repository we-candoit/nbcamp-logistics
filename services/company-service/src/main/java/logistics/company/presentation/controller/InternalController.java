package logistics.company.presentation.controller;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import logistics.company.application.service.CompanyService;
import logistics.company.presentation.dto.response.internal.CompanyBulkResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class InternalController {   // 내부 API
    private final CompanyService companyService;

    // 내부 API: 업체 복수 조회
    @GetMapping("/internal/companies")
    public ResponseEntity<CompanyBulkResponse> getCompanies(@RequestParam(value = "ids", required = false)
                                                            @NotEmpty(message = "조회할 업체 ID가 없습니다.")
                                                            @Size(max = 100, message = "한 번에 최대 100개까지 조회할 수 있습니다.")
                                                            List<UUID> ids) {
        return ResponseEntity.ok(companyService.getCompanies(ids));
    }
}

package logistics.company.presentation.controller;

import jakarta.validation.Valid;
import logistics.company.application.service.CompanyService;
import logistics.company.presentation.dto.request.company.CompanyCreateRequest;
import logistics.company.presentation.dto.request.company.CompanySearchCondition;
import logistics.company.presentation.dto.response.CompanyResponse;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;
    public static final String ROLE_HEADER = "X-Role-Name";

    @PostMapping("/api/companies")
    public ResponseEntity<CompanyResponse> createCompany(@RequestAttribute(ROLE_HEADER) String role,
                                                 @Valid @RequestBody CompanyCreateRequest request) {
        CompanyResponse response = companyService.createCompany(role, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 업체 목록 검색 (업체명·주소 부분 일치, 페이징)
    @GetMapping("/api/companies")
    public ResponseEntity<Page<CompanyResponse>> searchCompanies(
            @ParameterObject @ModelAttribute CompanySearchCondition condition,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ResponseEntity.ok(companyService.searchCompanies(condition, pageable));
    }

    // 업체 상세 조회
    @GetMapping("/api/companies/{companyId}")
    public ResponseEntity<CompanyResponse> getCompany(@PathVariable("companyId") UUID companyId) {
        return ResponseEntity.ok(companyService.getCompany(companyId));
    }


}

package logistics.company.presentation.controller;

import jakarta.validation.Valid;
import logistics.company.application.service.CompanyService;
import logistics.company.domain.entity.Company;
import logistics.company.presentation.dto.request.company.CompanyCreateRequest;
import logistics.company.presentation.dto.response.CompanyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    @PostMapping("/api/companies")
    public ResponseEntity<CompanyResponse> createCompany(@RequestAttribute("X-User-Role") String role,
                                                 @Valid @RequestBody CompanyCreateRequest request) {
        CompanyResponse response = companyService.createCompany(role, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

}

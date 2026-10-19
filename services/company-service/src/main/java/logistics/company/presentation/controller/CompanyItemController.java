package logistics.company.presentation.controller;

import jakarta.validation.Valid;
import logistics.company.application.service.CompanyItemService;
import logistics.company.presentation.dto.request.companyItem.CompanyItemCreateRequest;
import logistics.company.presentation.dto.response.CompanyItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CompanyItemController {
    private final CompanyItemService companyItemService;

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String ROLE_HEADER = "X-Role-Name";

    // 상품 등록
    @PostMapping("/api/companies/{companyId}/items")
    public ResponseEntity<CompanyItemResponse> createItem(@PathVariable("companyId") UUID companyId,
                                                          @RequestAttribute(USER_ID_HEADER) UUID userId,
                                                          @RequestAttribute(ROLE_HEADER) String role,
                                                          @Valid @RequestBody CompanyItemCreateRequest request) {

        CompanyItemResponse response = companyItemService.createItem(companyId, userId, role, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

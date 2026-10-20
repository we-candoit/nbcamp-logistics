package logistics.company.presentation.controller;

import jakarta.validation.Valid;
import logistics.company.application.service.CompanyItemService;
import logistics.company.presentation.dto.request.companyItem.CompanyItemCreateRequest;
import logistics.company.presentation.dto.request.companyItem.CompanyItemSearchCondition;
import logistics.company.presentation.dto.request.companyItem.CompanyItemUpdateRequest;
import logistics.company.presentation.dto.response.CompanyItemResponse;
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

    // 상품 상세 조회
    @GetMapping("/api/items/{itemId}")
    public ResponseEntity<CompanyItemResponse> getItem(@PathVariable("itemId") UUID itemId) {
        return ResponseEntity.ok(companyItemService.getItem(itemId));
    }

    // 상품 목록 검색 (업체 내 상품 조회는 ?companyId= 로 처리)
    @GetMapping("/api/items")
    public ResponseEntity<Page<CompanyItemResponse>> searchItems(
            @ParameterObject @Valid @ModelAttribute CompanyItemSearchCondition condition,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ResponseEntity.ok(companyItemService.searchItems(condition, pageable));
    }

    // 상품 수정
    @PatchMapping("/api/items/{itemId}")
    public ResponseEntity<CompanyItemResponse> updateItem(@PathVariable("itemId") UUID itemId,
                                                         @RequestAttribute(USER_ID_HEADER) UUID userId,
                                                         @RequestAttribute(ROLE_HEADER) String role,
                                                         @Valid @RequestBody CompanyItemUpdateRequest request) {
        return ResponseEntity.ok(companyItemService.updateItem(itemId, userId, role, request));
    }
}

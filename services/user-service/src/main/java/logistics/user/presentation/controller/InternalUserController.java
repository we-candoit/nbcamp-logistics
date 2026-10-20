package logistics.user.presentation.controller;

import jakarta.validation.Valid;
import logistics.user.application.service.UserService;
import logistics.user.presentation.dto.request.CompanyConfirmRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/users")
public class InternalUserController {

    private final UserService userService;

    // 업체 생성 후 업체 관리자 소속을 확정할 때 호출
    @PatchMapping("/{id}/affiliation")
    public ResponseEntity<Void> confirmCompany(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CompanyConfirmRequest request) {
        userService.confirmCompany(id, request.getCompanyId(), request.getCompanyName());
        return ResponseEntity.ok().build();
    }
}
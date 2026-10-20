package logistics.user.presentation.controller;

import jakarta.validation.Valid;
import logistics.user.application.service.AuthService;
import logistics.user.presentation.dto.request.JoinRequest;
import logistics.user.presentation.dto.request.LoginRequest;
import logistics.user.presentation.dto.response.ApiResponse;
import logistics.user.presentation.dto.response.JoinResponse;
import logistics.user.presentation.dto.response.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // 회원가입
    @PostMapping("/join")
    public ResponseEntity<ApiResponse<JoinResponse>> join(@Valid @RequestBody JoinRequest request) {
        JoinResponse joinResponse = authService.join(request.getLoginId(), request.getPassword(), request.getName(), request.getEmail(), request.getSlackId(), request.getAffiliationType(), request.getAffiliationName());
        ApiResponse<JoinResponse> apiResponse = ApiResponse.ok("회원가입이 완료되었습니다.", joinResponse);
        return ResponseEntity.status(HttpStatus.CREATED).body(apiResponse);
    }

    // 로그인
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse loginResponse = authService.login(request.getLoginId(), request.getPassword());
        ApiResponse<LoginResponse> apiResponse = ApiResponse.ok(loginResponse);
        return ResponseEntity.ok(apiResponse);
    }
}
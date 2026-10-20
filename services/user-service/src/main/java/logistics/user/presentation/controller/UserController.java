package logistics.user.presentation.controller;

import jakarta.validation.Valid;
import logistics.user.application.service.UserService;
import logistics.user.common.constant.UserHeaders;
import logistics.user.domain.enums.UserRole;
import logistics.user.presentation.dto.request.UserUpdateRequest;
import logistics.user.presentation.dto.response.ApiResponse;
import logistics.user.presentation.dto.response.UserDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    // 내 정보 상세 조회 - 유저, 관리자 가능
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDetailResponse>> getMyProfile(
            @PathVariable(name = "id") UUID id,
            @RequestHeader(UserHeaders.USER_ID) UUID requesterId,
            @RequestHeader(UserHeaders.ROLE_NAME) UserRole requesterRole
    ) {
        UserDetailResponse myProfile = userService.getMyProfile(requesterId, id, requesterRole);
        ApiResponse<UserDetailResponse> apiResponse = ApiResponse.ok(myProfile);
        return ResponseEntity.ok(apiResponse);
    }

    // 유저 전체 조회 - 관리자 전용 (검색조건 및 정렬기능 적용 전)
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserDetailResponse>>> getMyProfile(@RequestHeader(UserHeaders.ROLE_NAME) UserRole role) {
        List<UserDetailResponse> myProfile = userService.findAll(role);
        ApiResponse<List<UserDetailResponse>> apiResponse = ApiResponse.ok(myProfile);
        return ResponseEntity.ok(apiResponse);
    }

    // 유저 정보 수정
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDetailResponse>> updateUser(
            @PathVariable("id") UUID id, @Valid @RequestBody UserUpdateRequest request,
            @RequestHeader(UserHeaders.USER_ID) UUID requestorId
            ) {
        UserDetailResponse myProfile = userService.updateUser(requestorId, id, request.getName(), request.getEmail());
        ApiResponse<UserDetailResponse> apiResponse = ApiResponse.ok("사용자 정보가 수정되었습니다.", myProfile);

        return ResponseEntity.ok(apiResponse);
    }

}
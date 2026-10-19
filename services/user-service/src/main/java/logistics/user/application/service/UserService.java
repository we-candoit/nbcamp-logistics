package logistics.user.application.service;

import logistics.user.domain.entity.User;
import logistics.user.domain.enums.UserRole;
import logistics.user.infrastructure.repository.UserRepository;
import logistics.user.presentation.dto.response.UserDetailResponse;
import logistics.user.presentation.exception.CustomException;
import logistics.user.presentation.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    // 내 정보 상세 조회 - 유저, 관리자 가능
    public UserDetailResponse getMyProfile(UUID requesterId, UUID targetId, UserRole requesterRole) {

        // 본인이거나 관리자만 조회 가능
        if (!targetId.equals(requesterId) && !requesterRole.equals(UserRole.MASTER)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        // targetId: 조회하고자 하는 유저ID, requesterId: API 요청한 사람ID
        User user = userRepository.findById(targetId).orElseThrow(
                () -> new CustomException(ErrorCode.USER_NOT_FOUND)
        );

        return new UserDetailResponse(user);
    }

    // 유저 전체 조회 - 관리자 전용
    public List<UserDetailResponse> findAll(UserRole role) {
        if (role != UserRole.MASTER) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        List<UserDetailResponse> responses = new ArrayList<>();
        List<User> users = userRepository.findAll();
        for (User user : users) {
            responses.add(new UserDetailResponse(user));
        }

        return responses;
    }

    // 유저 정보 수정
    @Transactional
    public UserDetailResponse updateUser(UUID requestorId, UUID targetId, String name, String email) {
        if (!requestorId.equals(targetId)) {
            throw new CustomException(ErrorCode.FORBIDDEN_NOT_OWNER);
        }

        User user = userRepository.findById(targetId).orElseThrow(
                () -> new CustomException(ErrorCode.USER_NOT_FOUND)
        );

        user.updateProfile(name, email);
        return new UserDetailResponse(user);
    }
}
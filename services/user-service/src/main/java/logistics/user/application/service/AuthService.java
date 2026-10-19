package logistics.user.application.service;

import feign.FeignException;
import logistics.user.domain.entity.User;
import logistics.user.domain.enums.AffiliationType;
import logistics.user.infrastructure.client.HubClient;
import logistics.user.infrastructure.client.HubResponse;
import logistics.user.infrastructure.repository.UserRepository;
import logistics.user.presentation.dto.response.JoinResponse;
import logistics.user.presentation.exception.CustomException;
import logistics.user.presentation.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final PasswordEncoder passwordEncoder;
    private final HubClient hubClient;
    private final UserRepository userRepository;

    // 회원가입
    @Transactional
    public JoinResponse join(String loginId, String rawPassword, String name, String email, String slackId, AffiliationType affiliationType, String affiliationName) {

        String validatedEmail = (email == null || email.isBlank()) ? null : email.trim();

        if (userRepository.existsByLoginId(loginId)) {
            throw new CustomException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        if (validatedEmail != null && userRepository.existsByEmail(validatedEmail)) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsBySlackId(slackId)) {
            throw new CustomException(ErrorCode.DUPLICATE_SLACK_ID);
        }

        String encodedPassword = passwordEncoder.encode(rawPassword);
        User user;
        // 허브 소속 회원 생성
        if (affiliationType == AffiliationType.HUB) {
            HubResponse hubResponse = findHub(affiliationName);
            user = User.createHubUser(loginId, encodedPassword, name, validatedEmail, slackId, hubResponse.getHubId(), hubResponse.getHubName());
        } else {
            // 업체 소속 회원 생성
            user = User.createCompanyUser(loginId, encodedPassword, name, validatedEmail, slackId, affiliationName);
        }
        userRepository.save(user);
        return new JoinResponse(user);
    }

    // 유저가 잘못 입력한 경우와 Hub Server 자체 문제인 경우를 구분하기 위한 메서드
    private HubResponse findHub(String hubName) {
        try {
            return hubClient.getHubByName(hubName);
        } catch (FeignException.NotFound e) {
            throw new CustomException(ErrorCode.HUB_NOT_FOUND);
        } catch (FeignException e) {
            throw new CustomException(ErrorCode.HUB_SERVICE_UNAVAILABLE);
        }
    }
}
package logistics.user.application.service;

import feign.FeignException;
import logistics.user.domain.entity.User;
import logistics.user.domain.enums.UserRole;
import logistics.user.infrastructure.client.CompanyClient;
import logistics.user.infrastructure.client.CompanyResponse;
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

    private final CompanyClient companyClient;
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

    // 업체 관리자로 등록하며 업체 소속 신청
    @Transactional
    public void confirmCompany(UUID userId, UUID companyId, String companyName) {
        User user = userRepository.findById(userId).orElseThrow(
                () -> new CustomException(ErrorCode.USER_NOT_FOUND)
        );
        user.confirmCompany(companyId, companyName);
    }

    // 이미 존재하는 회사에 소속 신청
    @Transactional
    public UserDetailResponse applyCompany(UUID requesterId, UserRole requesterRole, String companyName) {
        User user = userRepository.findById(requesterId).orElseThrow(
                () -> new CustomException(ErrorCode.USER_NOT_FOUND)
        );
        CompanyResponse company = findCompany(requesterId, requesterRole, companyName);
        user.confirmCompany(company.getCompanyId(), company.getName());
        return new UserDetailResponse(user);
    }

    private CompanyResponse findCompany(UUID requesterId, UserRole requesterRole, String companyName) {
        List<CompanyResponse> companies;
        try {
            companies = companyClient.searchCompanies(companyName, 50, requesterId, requesterRole.name()).getContent();
        } catch (FeignException.NotFound e) {
            throw new CustomException(ErrorCode.COMPANY_NOT_FOUND);
        } catch (FeignException e) {
            throw new CustomException(ErrorCode.COMPANY_SERVICE_UNAVAILABLE);
        }
        // 이름에 해당하는 업체가 아예 없을 때
        if (companies.isEmpty()) {
            throw new CustomException(ErrorCode.COMPANY_NOT_FOUND);
        }
        // 업체가 부분 검색이라 정확히 일치하는 이름을 반환
        for (CompanyResponse company : companies) {
            if (company.getName().equals(companyName)) {
                return company;
            }
        }
        throw new CustomException(ErrorCode.COMPANY_NOT_FOUND);
    }
}
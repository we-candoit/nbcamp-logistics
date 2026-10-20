package logistics.user.domain.entity;

import jakarta.persistence.*;
import logistics.user.common.entity.BaseEntity;
import logistics.user.domain.enums.AffiliationStatus;
import logistics.user.domain.enums.AffiliationType;
import logistics.user.domain.enums.UserRole;
import logistics.user.presentation.exception.CustomException;
import logistics.user.presentation.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

@Getter
@Entity
@Table(name = "p_users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class User extends BaseEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String loginId; // 로그인할 때 쓰는 ID

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 100)
    private String name; // 회원 이름

    @Column(unique = true)
    private String email; // 이메일 필수X

    @Column(nullable = false, length = 100, unique = true)
    private String slackId;

    @Column(nullable = false)
    private boolean isDeliveryManager; // 배송담당자인지 확인[HUB소속은 true, COMPANY소속은 false]

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role; // [USER, MASTER] 유저 권한

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AffiliationType affiliationType;  // [HUB,COMPANY] 어디 소속인지

    @Column(nullable = false, length = 100)
    private String affiliationName; // 소속 허브or업체 명

    private UUID hubId; // 허브 소속으로 회원가입 시 할당 허브명 검증 후 할당
    private UUID companyId; // MASTER를 통한 업체 생성 및 소속 등록 시 할당

    // 허브or업체 소속 확정 상태, COMPANY 소속인데 PENDING인 경우 주문 불가능
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AffiliationStatus affiliationStatus; // [PENDING, CONFIRMED] 허브소속은 바로 CONFIRMED, 업체 소속은 PENDING -> 업체 생성 또는 소속 등록 시 CONFIRMED

    @Column(nullable = false)
    private boolean isPublic; // 정보 공개 가능 여부, 기본값: true

    private User(String loginId, String password, String name, String email, String slackId,
                 UserRole role, AffiliationType affiliationType,
                 String affiliationName, AffiliationStatus affiliationStatus) {
        this.id = UUID.randomUUID();
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.email = email;
        this.slackId = slackId;
        this.isDeliveryManager = false; // 배송 담당자 기본 false, 권한부여 메서드로 부여함
        this.role = role;
        this.affiliationType = affiliationType;
        this.affiliationName = affiliationName;
        this.affiliationStatus = affiliationStatus;
        this.isPublic = true;
        initAuditor(this.id.toString()); // 생성자, 수정자 설정
    }

    // 허브 소속 회원가입
    public static User createHubUser(String loginId, String password, String name, String email, String slackId, UUID hubId, String hubName) {
        if (hubId == null) {
            throw new IllegalArgumentException("허브 소속은 hubID가 필요합니다.");
        }
        User user = new User(loginId, password, name, email, slackId,
                UserRole.USER, AffiliationType.HUB, hubName, AffiliationStatus.CONFIRMED);
        user.grantDeliveryManager(); // 허브 유저는 배송 담당자 권한 부여
        user.hubId = hubId;

        return user;
    }

    // 업체 소속 회원가입
    public static User createCompanyUser(String loginId, String password, String name, String email, String slackId, String companyName) {
        return new User(loginId, password, name, email, slackId,
                UserRole.USER, AffiliationType.COMPANY, companyName, AffiliationStatus.PENDING);
    }

    // 배송담당자 권한 부여
    public void grantDeliveryManager() {
        this.isDeliveryManager = true;
    }

    // 배송 담당자 권한 철회
    public void revokeDeliveryManager() {
        this.isDeliveryManager = false;
    }

    // id를 직접 할당하므로 save() 시 불필요한 SELECT 쿼리 없이 INSERT 하도록 신규 여부 알려준다.
    @Override
    public boolean isNew() {
        return getCreatedAt() == null;
    }

    public void updateProfile(String name, String email) {
        if (name != null) {
            this.name = name;
        }
        if (email != null) {
            this.email = email;
        }
    }

    // 업체 소속 확정 시 유저 정보 업데이트
    public void confirmCompany(UUID companyId, String companyName) {
        // COMPANY이면서, PENDING 상태여야 가능
        if (affiliationType != AffiliationType.COMPANY || affiliationStatus != AffiliationStatus.PENDING) {
            throw new CustomException(ErrorCode.INVALID_AFFILIATION_STATUS);
        }
        this.companyId = companyId;
        this.affiliationName = companyName;
        this.affiliationStatus = AffiliationStatus.CONFIRMED;
    }
}
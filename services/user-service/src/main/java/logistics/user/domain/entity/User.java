package logistics.user.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import logistics.user.common.entity.BaseEntity;
import logistics.user.domain.enums.UserRole;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

@Getter
@Entity
@Table(name = "p_users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String loginId; // 로그인할 때 쓰는 ID

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 100)
    private String name; // 회원 이름

    @Column(unique = true)
    private String email;

    @Column(nullable = false, length = 100, unique = true)
    private String slackId;

    @Column(nullable = false)
    private boolean isDeliveryManager; // 배송담당자인지 확인, 기본값: false(메서드로 신청)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(nullable = false, length = 100)
    private String affiliationName; // 소속 업체명(회원가입 시 제출)

    @Column(nullable = false)
    private boolean isPublic; // 정보 공개 가능 여부, 기본값: true

    private User(String loginId, String password, String name, String email, String slackId, String affiliationName) {
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.email = email;
        this.slackId = slackId;
        this.isDeliveryManager = false;
        this.role = UserRole.USER;
        this.affiliationName = affiliationName;
        this.isPublic = true;
    }

    public static User create(String loginId, String password, String name, String email, String slackId, String affiliationName) {
        return new User(loginId, password, name, email, slackId, affiliationName);
    }

    // 배송담당자 권한 부여
    public void grantDeliveryManager() {
        this.isDeliveryManager = true;
    }

    // 배송 담당자 권한 철회
    public void revokeDeliveryManager() {
        this.isDeliveryManager = false;
    }
}
package logistics.company.domain.entity;

import jakarta.persistence.*;
import logistics.company.common.entity.BaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

@Entity
@Table(name = "p_companies")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Company extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "hub_id", nullable = false)
    private UUID hubId;            // 관리 허브 ID

    @Column(name = "manager_user_id", nullable = false)
    private UUID managerUserId;   // 업체 관리자 ID

    @Column(nullable = false, length = 100)
    private String name;            // 업체명

    @Column(nullable = false, length = 255)
    private String address;         // 업체 주소

    private Company(UUID hubId, UUID managerUserId, String name, String address) {
        this.hubId = hubId;
        this.managerUserId = managerUserId;
        this.name = name;
        this.address = address;
    }
    public static Company create(UUID hubId, UUID managerUserId, String name, String address) {
        return new Company(hubId, managerUserId, name, address);
    }

    public void changeName(String name) {
        this.name = name;
    }

    public void changeAddress(String address) {
        this.address = address;
    }

    public void changeHub(UUID hubId) {
        this.hubId = hubId;
    }

    public void changeManager(UUID managerUserId) {
        this.managerUserId = managerUserId;
    }

    public boolean isManagedBy(UUID userId) {
        return this.managerUserId.equals(userId);
    }
}

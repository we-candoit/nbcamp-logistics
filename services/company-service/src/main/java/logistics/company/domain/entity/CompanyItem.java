package logistics.company.domain.entity;

import jakarta.persistence.*;
import logistics.company.common.entity.BaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

@Entity
@Table(name = "p_company_items")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanyItem extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;         // 소속 업체 ID

    @Column(nullable = false)
    private Long price;             // 상품 가격

    @Column(nullable = false, length = 100)
    private String name;            // 업체 상품명

    @Column(length = 255)
    private String description;     // 상품 설명

    private CompanyItem(Company company, String name, Long price, String description) {
        this.company = company;
        this.name = name;
        this.price = price;
        this.description = description;
    }

    public static CompanyItem create(Company company, String name, Long price, String description) {
        return new CompanyItem(company, name, price, description);
    }

    public void changeName(String name) {
        this.name = name;
    }

    public void changePrice(Long price) {
        this.price = price;
    }

    public void changeDescription(String description) {
        this.description = description;
    }
}

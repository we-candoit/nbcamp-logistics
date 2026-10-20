package logistics.company.infrastructure.repository;

import logistics.company.domain.entity.CompanyItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CompanyItemRepository extends JpaRepository<CompanyItem, UUID> {
    boolean existsByCompanyIdAndName(UUID companyId, String itemName);

    // 내부 복수 조회: 업체 정보를 함께 조회 (N+1 방지), 삭제된 상품은 @SQLRestriction으로 제외
    @EntityGraph(attributePaths = "company")
    List<CompanyItem> findAllByIdIn(List<UUID> ids);
}

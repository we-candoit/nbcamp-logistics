package logistics.company.infrastructure.repository;

import logistics.company.domain.entity.CompanyItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CompanyItemRepository extends JpaRepository<CompanyItem, UUID> {
    boolean existsByCompanyIdAndName(UUID companyId, String itemName);
}

package logistics.company.infrastructure.repository;

import logistics.company.domain.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {
    boolean existsByManagerUserId(UUID managerUserId);
}
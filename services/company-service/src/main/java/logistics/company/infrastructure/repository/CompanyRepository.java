package logistics.company.infrastructure.repository;

import logistics.company.domain.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID>,  JpaSpecificationExecutor<Company> {
    boolean existsByManagerUserId(UUID managerUserId);
}
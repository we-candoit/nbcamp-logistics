package logistics.user.infrastructure.repository;

import logistics.user.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);

    boolean existsBySlackId(String slackId);
}
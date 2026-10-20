package logistics.user.config;

import logistics.user.domain.entity.User;
import logistics.user.infrastructure.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// MASTER는 회원가입으로 만들 수 없어서 서버 시작 시 env 값으로 생성
@Component
public class MasterAccountInit implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String loginId;
    private final String password;
    private final String name;
    private final String slackId;

    public MasterAccountInit(
            UserRepository userRepository, PasswordEncoder passwordEncoder,
            @Value("${master.login-id}") String loginId,
            @Value("${master.password}") String password,
            @Value("${master.name}") String name,
            @Value("${master.slack-id}") String slackId
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.slackId = slackId;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 재시작 시 중복 생성 방지 (ddl-auto: update로 바꾼 뒤에도 안전하게)
        if (userRepository.existsByLoginId(loginId)) {
            return;
        }
        User master = User.createMaster(loginId, passwordEncoder.encode(password), name, slackId);
        userRepository.save(master);
    }
}
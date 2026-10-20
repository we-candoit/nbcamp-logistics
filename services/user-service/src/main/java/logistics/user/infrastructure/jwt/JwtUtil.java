package logistics.user.infrastructure.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import logistics.user.domain.enums.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final long expiration; // 1시간으로 설정함

    public JwtUtil(@Value("${jwt.secret-key}") String secretKey, @Value("${jwt.expiration}") long expiration) {
        // Base64로 인코딩된 비밀 키 문자열을 Byte로 변환
        byte[] decodedKey = Decoders.BASE64.decode(secretKey);
        this.secretKey = Keys.hmacShaKeyFor(decodedKey); // 만든 Byte로 서명용 키 객체 생성
        this.expiration = expiration;
    }

    public String createToken(UUID userId, UserRole role) {
        Date date = new Date();

        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role.name())
                .issuedAt(date)
                .expiration(new Date(date.getTime() + expiration))
                .signWith(secretKey)
                .compact();
    }
}
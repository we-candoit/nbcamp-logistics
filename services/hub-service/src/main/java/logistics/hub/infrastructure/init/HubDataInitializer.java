package logistics.hub.infrastructure.init;

import logistics.hub.domain.entity.Hub;
import logistics.hub.infrastructure.repository.HubRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component

@RequiredArgsConstructor
public class HubDataInitializer implements CommandLineRunner {

    private final HubRepository hubRepository;

    @Override
    @Transactional
    public void run(String... args) {

        List<Hub> initialHubs = List.of(
                new Hub(
                        "서울특별시 센터",
                        "서울특별시 송파구 송파대로 55",
                        37.5665,
                        126.9780,
                        "MASTER"
                ),
                new Hub(
                        "경기 북부 센터",
                        "경기도 고양시 덕양구 권율대로 570",
                        37.7634,
                        127.0601,
                        "MASTER"
                ),
                new Hub(
                        "경기 남부 센터",
                        "경기도 이천시 덕평로 257-21",
                        37.2636,
                        127.0286,
                        "MASTER"
                ),
                new Hub(
                        "부산광역시 센터",
                        "부산 동구 중앙대로 206",
                        35.1796,
                        129.0756,
                        "MASTER"
                ),
                new Hub(
                        "대구광역시 센터",
                        "대구 북구 태평로 161",
                        35.8722,
                        128.6014,
                        "MASTER"
                ),
                new Hub(
                        "인천광역시 센터",
                        "인천 남동구 정각로 29",
                        37.4563,
                        126.7052,
                        "MASTER"
                ),
                new Hub(
                        "광주광역시 센터",
                        "광주 서구 내방로 111",
                        35.1595,
                        126.8526,
                        "MASTER"
                ),
                new Hub(
                        "대전광역시 센터",
                        "대전 서구 둔산로 100",
                        36.3504,
                        127.3845,
                        "MASTER"
                ),
                new Hub(
                        "울산광역시 센터",
                        "울산 남구 중앙로 201",
                        35.5384,
                        129.3114,
                        "MASTER"
                ),
                new Hub(
                        "세종특별자치시 센터",
                        "세종특별자치시 한누리대로 2130",
                        36.4801,
                        127.2889,
                        "MASTER"
                ),
                new Hub(
                        "강원특별자치도 센터",
                        "강원특별자치도 춘천시 중앙로 1",
                        37.8228,
                        128.1555,
                        "MASTER"
                ),
                new Hub(
                        "충청북도 센터",
                        "충북 청주시 상당구 상당로 82",
                        36.6357,
                        127.4914,
                        "MASTER"
                ),
                new Hub(
                        "충청남도 센터",
                        "충남 홍성군 홍북읍 충남대로 21",
                        36.6588,
                        126.6730,
                        "MASTER"
                ),
                new Hub(
                        "전북특별자치도 센터",
                        "전북특별자치도 전주시 완산구 효자로 225",
                        35.8204,
                        127.1088,
                        "MASTER"
                ),
                new Hub(
                        "전라남도 센터",
                        "전남 무안군 삼향읍 오룡길 1",
                        34.8161,
                        126.4630,
                        "MASTER"
                ),
                new Hub(
                        "경상북도 센터",
                        "경북 안동시 풍천면 도청대로 455",
                        36.5684,
                        128.7294,
                        "MASTER"
                ),
                new Hub(
                        "경상남도 센터",
                        "경남 창원시 의창구 중앙대로 300",
                        35.2286,
                        128.6628,
                        "MASTER"
                )
        );

        int insertedCount = 0;

        for (Hub hub : initialHubs) {
            if (!hubRepository.existsByName(hub.getName())) {
                hubRepository.save(hub);
                insertedCount++;
            }
        }

        log.info("허브 초기 데이터 등록: {}개 추가", insertedCount);
    }
}
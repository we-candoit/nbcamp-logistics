package logistics.hub.infrastructure.init;

import logistics.hub.domain.entity.Hub;
import logistics.hub.infrastructure.repository.HubRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
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
                        "SYSTEM"
                ),
                new Hub(
                        "경기 북부 센터",
                        "경기도 고양시 덕양구 권율대로 570",
                        "SYSTEM"
                ),
                new Hub(
                        "경기 남부 센터",
                        "경기도 이천시 덕평로 257-21",
                        "SYSTEM"
                ),
                new Hub(
                        "부산광역시 센터",
                        "부산 동구 중앙대로 206",
                        "SYSTEM"
                ),
                new Hub(
                        "대구광역시 센터",
                        "대구 북구 태평로 161",
                        "SYSTEM"
                ),
                new Hub(
                        "인천광역시 센터",
                        "인천 남동구 정각로 29",
                        "SYSTEM"
                ),
                new Hub(
                        "광주광역시 센터",
                        "광주 서구 내방로 111",
                        "SYSTEM"
                ),
                new Hub(
                        "대전광역시 센터",
                        "대전 서구 둔산로 100",
                        "SYSTEM"
                ),
                new Hub(
                        "울산광역시 센터",
                        "울산 남구 중앙로 201",
                        "SYSTEM"
                ),
                new Hub(
                        "세종특별자치시 센터",
                        "세종특별자치시 한누리대로 2130",
                        "SYSTEM"
                ),
                new Hub(
                        "강원특별자치도 센터",
                        "강원특별자치도 춘천시 중앙로 1",
                        "SYSTEM"
                ),
                new Hub(
                        "충청북도 센터",
                        "충북 청주시 상당구 상당로 82",
                        "SYSTEM"
                ),
                new Hub(
                        "충청남도 센터",
                        "충남 홍성군 홍북읍 충남대로 21",
                        "SYSTEM"
                ),
                new Hub(
                        "전북특별자치도 센터",
                        "전북특별자치도 전주시 완산구 효자로 225",
                        "SYSTEM"
                ),
                new Hub(
                        "전라남도 센터",
                        "전남 무안군 삼향읍 오룡길 1",
                        "SYSTEM"
                ),
                new Hub(
                        "경상북도 센터",
                        "경북 안동시 풍천면 도청대로 455",
                        "SYSTEM"
                ),
                new Hub(
                        "경상남도 센터",
                        "경남 창원시 의창구 중앙대로 300",
                        "SYSTEM"
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
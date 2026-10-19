package logistics.company.infrastructure.repository;

import logistics.company.domain.entity.Company;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.criteria.Predicate;

public final class CompanySpecification {       // 검색 조건 클래스

    private CompanySpecification() {
    }

    // 업체명·주소 부분 일치 검색 (값이 있는 조건만 AND로 결합, 대소문자 구분 안 함)
    public static Specification<Company> search(String companyName, String address) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(companyName)) { // null, 빈 문자열, 공백만 있는 값을 모두 "조건 없음"으로 처리
                predicates.add(cb.like(cb.lower(root.get("name")),
                        "%" + companyName.trim().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(address)) {
                predicates.add(cb.like(cb.lower(root.get("address")),
                        "%" + address.trim().toLowerCase() + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));    // 조건이 하나도 없으면 ->  전체 목록을 조회
        };
    }
}
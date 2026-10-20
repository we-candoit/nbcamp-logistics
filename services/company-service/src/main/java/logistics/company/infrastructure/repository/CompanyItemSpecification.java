package logistics.company.infrastructure.repository;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import logistics.company.domain.entity.Company;
import logistics.company.domain.entity.CompanyItem;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CompanyItemSpecification {      // 상품 검색 조건 클래스

    private CompanyItemSpecification() {
    }

    // 값이 있는 조건만 AND로 결합 (문자열: 부분 일치, 대소문자 구분 안 함 / ID: 일치 / 가격: 경계값 포함)
    public static Specification<CompanyItem> search(UUID itemId, UUID companyId,
                                                    String itemName, String companyName, String description,
                                                    Long minPrice, Long maxPrice) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (itemId != null) {
                predicates.add(cb.equal(root.get("id"), itemId));
            }
            if (companyId != null) {
                predicates.add(cb.equal(root.get("company").get("id"), companyId));
            }
            if (StringUtils.hasText(itemName)) {
                predicates.add(cb.like(cb.lower(root.get("name")),
                        "%" + itemName.trim().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(description)) {
                predicates.add(cb.like(cb.lower(root.get("description")),
                        "%" + description.trim().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(companyName)) {  // 업체명(업체 DB) 조건이 있을 때만 업체 테이블 조인
                Join<CompanyItem, Company> company = root.join("company");
                predicates.add(cb.like(cb.lower(company.get("name")),
                        "%" + companyName.trim().toLowerCase() + "%"));
            }
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            }

            return cb.and(predicates.toArray(new Predicate[0]));    // // 조건이 하나도 없으면 ->  전체 목록을 조회
        };
    }
}

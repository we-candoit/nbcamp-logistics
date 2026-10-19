package logistics.delivery.infrastructure.repository;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import logistics.delivery.domain.entity.Delivery;
import logistics.delivery.domain.entity.DeliveryRoute;
import logistics.delivery.domain.enums.DeliveryStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

/**
 * DeliveryRepository에서 사용할 검색 조건을 만드는 클래스
 * SQL쿼리에서 WHERE 조건을 담당하는 클래스
 * QueryDSL 대신 사용
 */
public class DeliverySpecification{

    private DeliverySpecification(){}

    public static Specification<Delivery> isNotDeleted(){
        return (root, query, cb) ->
                cb.isNull(root.get("deletedAt"));
    }

    public static Specification<Delivery> hasStatus(DeliveryStatus status){
        return (root, query, cb) ->
                status == null
                        ? cb.conjunction()
                        : cb.equal(root.get("status"), status);

    }
    public static Specification<Delivery> hasDepartureHubId(UUID departureHubId) {
        return (root, query, cb) ->
                departureHubId == null
                        ? cb.conjunction()
                        : cb.equal(root.get("departureHubId"), departureHubId);
    }

    public static Specification<Delivery> hasDestinationHubId(UUID destinationHubId) {
        return (root, query, cb) ->
                destinationHubId == null
                        ? cb.conjunction()
                        : cb.equal(root.get("destinationHubId"), destinationHubId);
    }

    public static Specification<Delivery> hasOrderId(UUID orderId) {
        return (root, query, cb) ->
                orderId == null
                        ? cb.conjunction()
                        : cb.equal(root.get("orderId"), orderId);
    }

    // 입력한 사용자가 배송 담당자 이거나, 배송 경로 담당자인 배송을 찾는 검색 조건을 만드는 메서드
    public static Specification<Delivery> hasManagerId(UUID managerId) {
        return (root, query, cb) -> {
            if (managerId == null) {
                return cb.conjunction();
            }

            Subquery<Integer> routeQuery = query.subquery(Integer.class);
            Root<DeliveryRoute> route = routeQuery.from(DeliveryRoute.class);

            routeQuery.select(cb.literal(1))
                    .where(
                            cb.equal(route.get("delivery"), root),
                            cb.equal(route.get("managerId"), managerId),
                            cb.isNull(route.get("deletedAt"))
                    );

            return cb.or(
                    cb.equal(root.get("managerId"), managerId),
                    cb.exists(routeQuery)
            );
        };
    }
}

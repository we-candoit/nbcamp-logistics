package logistics.hub.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import logistics.hub.common.entity.BaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Getter
@Table(name = "p_hub_routes", uniqueConstraints = {@UniqueConstraint(name = "uk_hub_routes_origin_destination", columnNames = {"origin_hub_id", "destination_hub_id"})})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HubRoute extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "origin_hub_id", nullable = false)
    private Hub originHub;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_hub_id", nullable = false)
    private Hub destinationHub;

    @Column(name = "distance", nullable = false,
            precision = 10, scale = 2)
    private BigDecimal distance;

    @Column(name = "duration", nullable = false)
    private Integer duration;

    public HubRoute(
            Hub originHub,
            Hub destinationHub,
            BigDecimal distance,
            Integer duration,
            String createdBy
    ) {
        super(createdBy);

        if (originHub.getId().equals(destinationHub.getId())) {
            throw new IllegalArgumentException(
                    "출발 허브와 도착 허브는 달라야 합니다."
            );
        }

        if (distance == null
                || distance.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "이동거리는 0보다 커야 합니다."
            );
        }

        if (duration == null || duration <= 0) {
            throw new IllegalArgumentException(
                    "소요시간은 0보다 커야 합니다."
            );
        }

        this.originHub = originHub;
        this.destinationHub = destinationHub;
        this.distance = distance;
        this.duration = duration;
    }

}
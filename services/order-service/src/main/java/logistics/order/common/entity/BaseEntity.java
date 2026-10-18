package logistics.order.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity implements Persistable<UUID> {

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @CreatedBy
    @Column(updatable = false, nullable = false)
    private UUID createdBy;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @LastModifiedBy
    @Column(nullable = false)
    private UUID updatedBy;

    @Column
    private LocalDateTime deletedAt;

    @Column
    private UUID deletedBy;

    @Override
    public boolean isNew() {
        return createdAt == null;
    }

    public void softDelete(UUID userId) {
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = userId;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}

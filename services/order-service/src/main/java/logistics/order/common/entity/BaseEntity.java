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

    public boolean isDeleted() { return deletedAt != null; }

    protected void softDelete(UUID userId) {
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = userId;
    }

    //softDelete 되돌리기 / deletedAt, deletedBy를 비워 삭제 전 상태로 복구 (보상처리에서 사용)
    //삭제되지 않은 경우 예외 없이 무시 (보상 중 예외가 발생하면 원래 실패 원인이 가려지므로)
    public void revertDelete() {
        if (!isDeleted()) {
            return;
        }

        this.deletedAt = null;
        this.deletedBy = null;
    }
}

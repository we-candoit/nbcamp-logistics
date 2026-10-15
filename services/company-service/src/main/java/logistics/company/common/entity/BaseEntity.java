package logistics.company.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.TemporalType;
import lombok.Getter;
import org.hibernate.annotations.Temporal;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@MappedSuperclass                                    // 테이블은 안 만들고 필드만 자식에게 상속
@EntityListeners(AuditingEntityListener.class)      // 저장/수정 이벤트 감지
public abstract class BaseEntity  {

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;    // 레코드 생성 시간

    @CreatedBy
    @Column(updatable = false)
    private LocalDateTime createdBy;    // 레코드 생성자


    @LastModifiedDate
    @Column(nullable = false)

    private LocalDateTime updatedAt;    // 레코드 수정 시간

    @LastModifiedBy
    @Column(length = 100, nullable = false)
    private LocalDateTime updatedBy;    // 레코드 수정자

    @Column
    private LocalDateTime deletedAt;    // 레코드 삭제 시간

    @Column(length = 100)
    private String deletedBy;           // 레코드 삭제자
}

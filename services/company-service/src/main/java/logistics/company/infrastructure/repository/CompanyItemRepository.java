package logistics.company.infrastructure.repository;

import logistics.company.domain.entity.CompanyItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyItemRepository extends JpaRepository<CompanyItem, UUID> {
    boolean existsByCompanyIdAndName(UUID companyId, String itemName);

    // 내부 복수 조회: 업체 정보를 함께 조회 (N+1 방지), 삭제된 상품은 @SQLRestriction으로 제외
    @EntityGraph(attributePaths = "company")
    List<CompanyItem> findAllByIdIn(List<UUID> ids);

    // 목록 검색: 업체를 함께 조회해 N+1 방지 (count 쿼리에는 적용되지 않음)
    @EntityGraph(attributePaths = "company")
    Page<CompanyItem> findAll(Specification<CompanyItem> search, Pageable pageable);

    // 단건 조회: 업체를 함께 조회 (업체명 가져오기)
    @EntityGraph(attributePaths = "company")
    Optional<CompanyItem> findById(UUID id);

    // flushAutomatically: 실행 전 영속성 컨텍스트의 변경사항(업체 삭제)을 먼저 DB에 반영
    // clearAutomatically: 벌크 UPDATE는 영속성 컨텍스트를 거치지 않으므로, 실행 후 비워서 이전 값 조회 방지
    @Modifying(flushAutomatically = true, clearAutomatically = true)  // 실행 전 변경사항 flush, 실행 후 영속성 컨텍스트 초기화 (벌크 UPDATE 정합성)
    @Query("UPDATE CompanyItem i SET i.deletedAt = :deletedAt, i.deletedBy = :deletedBy " +     // : 파라미터 자리
            "WHERE i.company.id = :companyId AND i.deletedAt IS NULL")      // companyId 일치, 상품 삭제 안 된 상품인지 확인(이미 삭제된 상품 삭제 기록 업데이트 금지)
    void softDeleteAllByCompanyId(@Param("companyId") UUID companyId,
                                  @Param("deletedBy") UUID deletedBy,
                                  @Param("deletedAt") LocalDateTime deletedAt);
}

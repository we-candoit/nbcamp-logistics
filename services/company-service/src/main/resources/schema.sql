-- 삭제되지 않은 업체 중 같은 관리자 중복 방지: 업체 관리자 : 업체 = 1:1
CREATE UNIQUE INDEX IF NOT EXISTS uk_company_manager_active
    ON p_companies (manager_user_id)
    WHERE deleted_at IS NULL;

-- 삭제되지 않은 상품 중 같은 업체 내 상품명 중복 방지
CREATE UNIQUE INDEX IF NOT EXISTS uk_company_item_name_active
    ON p_company_items (company_id, name)
    WHERE deleted_at IS NULL;
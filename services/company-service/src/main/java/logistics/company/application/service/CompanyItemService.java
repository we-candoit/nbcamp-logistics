package logistics.company.application.service;

import jakarta.validation.Valid;
import logistics.company.domain.entity.Company;
import logistics.company.domain.entity.CompanyItem;
import logistics.company.infrastructure.repository.CompanyItemRepository;
import logistics.company.infrastructure.repository.CompanyItemSpecification;
import logistics.company.infrastructure.repository.CompanyRepository;
import logistics.company.presentation.dto.request.companyItem.CompanyItemCreateRequest;
import logistics.company.presentation.dto.request.companyItem.CompanyItemSearchCondition;
import logistics.company.presentation.dto.response.CompanyItemResponse;
import logistics.company.presentation.exception.BusinessException;
import logistics.company.presentation.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyItemService {
    private final CompanyRepository companyRepository;
    private final CompanyItemRepository companyItemRepository;

    private static final String MASTER = "MASTER";

    @Transactional
    public CompanyItemResponse createItem(UUID companyId, UUID userId, String role, @Valid CompanyItemCreateRequest request) {
        // 1. 소속 업체 존재 확인
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_NOT_FOUND));

        // 2. 권한 체크 : MASTER 또는 해당 업체 관리자인지 확인
        if (!MASTER.equals(role) && !company.isManagedBy(userId)) { // MASTER or 해당 업체 관리자가 아닌 경우
            throw new BusinessException(ErrorCode.ITEM_FORBIDDEN);
        }

        // 3. 같은 업체 내 상품명 중복 확인: 앞뒤 공백 제거, 대소문자 확인
        String itemName = request.getName().trim();
        if(companyItemRepository.existsByCompanyIdAndName(companyId, itemName)) {
            throw new BusinessException(ErrorCode.ITEM_NAME_ALREADY_EXISTS);
        }

        // 4. 저장
        CompanyItem item = CompanyItem.create(
                company, itemName, request.getPrice(), request.getDescription()
        );

        CompanyItem saved = companyItemRepository.saveAndFlush(item);

        return CompanyItemResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public CompanyItemResponse getItem(UUID itemId) {
        CompanyItem item = companyItemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
        return CompanyItemResponse.from(item);
    }

    @Transactional(readOnly = true)
    public Page<CompanyItemResponse> searchItems(@Valid CompanyItemSearchCondition condition, Pageable pageable) {
        // 최소 가격 > 최대 가격이면 400, 잘못된 범위는 조회하지 않음
        if (condition.invalidPriceRange()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        return companyItemRepository.findAll(
                CompanyItemSpecification.search(
                        condition.getItemId(), condition.getCompanyId(),
                        condition.getItemName(), condition.getCompanyName(), condition.getDescription(),
                        condition.getMinPrice(), condition.getMaxPrice()
                ), pageable
        ).map(CompanyItemResponse::from);
    }
}

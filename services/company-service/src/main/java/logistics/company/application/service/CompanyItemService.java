package logistics.company.application.service;

import jakarta.validation.Valid;
import logistics.company.domain.entity.Company;
import logistics.company.domain.entity.CompanyItem;
import logistics.company.infrastructure.repository.CompanyItemRepository;
import logistics.company.infrastructure.repository.CompanyItemSpecification;
import logistics.company.infrastructure.repository.CompanyRepository;
import logistics.company.presentation.dto.request.companyItem.CompanyItemCreateRequest;
import logistics.company.presentation.dto.request.companyItem.CompanyItemSearchCondition;
import logistics.company.presentation.dto.request.companyItem.CompanyItemUpdateRequest;
import logistics.company.presentation.dto.response.CompanyItemResponse;
import logistics.company.presentation.dto.response.internal.CompanyItemBulkResponse;
import logistics.company.presentation.dto.response.internal.CompanyItemInternalResponse;
import logistics.company.presentation.exception.BusinessException;
import logistics.company.presentation.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

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

    @Transactional
    public CompanyItemResponse updateItem(UUID itemId, UUID userId, String role, CompanyItemUpdateRequest request) {
        // 1. 수정할 상품 존재 확인
        CompanyItem companyItem = companyItemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));

        // 2. 권한 체크 : MASTER 또는 해당 업체 관리자인지 확인
        if (!MASTER.equals(role) && !companyItem.isManagedBy(userId)) { // MASTER or 해당 업체 관리자가 아닌 경우
            throw new BusinessException(ErrorCode.ITEM_FORBIDDEN);
        }

        // 3. 기존 값과 비교해 실제로 바뀌는 항목만 판별 (null = 변경 안 함, 수정 취소)
        // 변경하는 상품명은 상품 등록처럼 앞뒤 공백 제거 후 비교, 저장
        String newName = request.getName() == null ? null : request.getName().trim();
        boolean nameChanged = isChanged(newName, companyItem.getName());
        boolean priceChanged = isChanged(request.getPrice(), companyItem.getPrice());
        boolean descriptionChanged = isChanged(request.getDescription(), companyItem.getDescription());

        // 바뀌는 항목이 없으면 400 (모두 null이거나 모두 기존과 같은 경우)
        if (!nameChanged && !priceChanged && !descriptionChanged) {
            throw new BusinessException(ErrorCode.NO_UPDATE_FIELDS);
        }

        // 4. 이미 존재하는 상품명과 중복되는지 확인
        if (nameChanged && companyItemRepository.existsByCompanyIdAndName(companyItem.getCompany().getId(), newName)) {
            throw new BusinessException(ErrorCode.ITEM_NAME_ALREADY_EXISTS);
        }

        // 5. 상품명, 가격, 설명 변경
        if (nameChanged) {
            companyItem.changeName(newName);
        }
        if (priceChanged) {
            companyItem.changePrice(request.getPrice());
        }
        if (descriptionChanged) {
            companyItem.changeDescription(request.getDescription());
        }

        // 6. 수정사항 즉시 반영
        companyItemRepository.flush();

        return CompanyItemResponse.from(companyItem);
    }

    private boolean isChanged(Object requested, Object current) {
        // 요청 값이 있고, 기존 값과 다르면 변경 대상
        return requested != null && !Objects.equals(requested, current);
    }

    // 내부 API: 업체 상품 복수 조회
    @Transactional(readOnly = true)
    public CompanyItemBulkResponse getItems(List<UUID> ids) {
        // 1. ids 중복 체크
        List<UUID> distinctIds = ids.stream().distinct().toList();

        // 2. ids별 상품 조회
        List<CompanyItem> foundItems = companyItemRepository.findAllByIdIn(distinctIds);

        // 3. map 형태로 정리
        Map<UUID, CompanyItem> companyItemMap = new HashMap<>();
        for (CompanyItem item : foundItems) {
            companyItemMap.put(item.getId(), item);
        }

        // 4. 응답 DTO 구조
        List<CompanyItemInternalResponse> responses = new ArrayList<>();
        List<UUID> notFoundIds = new ArrayList<>();

        // 5. 찾았으면 CompanyItemInternalResponse, 못 찾았으면 id만 notFoundIds 저장
        for (UUID id : distinctIds) {
            CompanyItem item = companyItemMap.get(id);
            if (item != null) {
                responses.add(CompanyItemInternalResponse.from(item));
            } else {
                notFoundIds.add(id);
            }
        }

        return CompanyItemBulkResponse.of(responses, notFoundIds);
    }
}

package logistics.hub.application.service;

import logistics.hub.presentation.dto.request.InventoryCreateRequest;
import logistics.hub.presentation.dto.response.InventoryResponse;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;


@Service
@RequiredArgsConstructor
public class InventoryRegistrationService {

    private final InventoryCompanyValidator companyValidator;
    private final InventoryWriter inventoryWriter;

    public InventoryResponse createInventory(
            UUID hubId,
            InventoryCreateRequest request,
            UUID userId,
            String role,
            String authorization
    ) {
        if (userId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "인증된 사용자 정보가 필요합니다."
            );
        }

        if (!"MASTER".equals(role)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "재고 등록은 MASTER만 가능합니다."
            );
        }

        companyValidator.validate(
                hubId,
                request.getCompanyId(),
                request.getProductId(),
                authorization
        );

        try {
            return inventoryWriter.create(
                    hubId,
                    request.getProductId(),
                    request.getQuantity(),
                    userId
            );
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateInventory(e)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "이미 등록된 재고입니다.",
                        e
                );
            }

            throw e;
        }
    }

    private boolean isDuplicateInventory(Throwable exception) {
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation) {
                return "uk_hub_items_hub_product"
                        .equals(violation.getConstraintName());
            }

            cause = cause.getCause();
        }

        return false;
    }
}
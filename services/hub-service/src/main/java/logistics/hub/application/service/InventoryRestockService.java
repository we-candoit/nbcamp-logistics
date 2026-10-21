package logistics.hub.application.service;

import logistics.hub.presentation.dto.request.InventoryRestockRequest;
import logistics.hub.presentation.dto.response.InventoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryRestockService {

    private final InventoryCompanyValidator companyValidator;
    private final InventoryWriter inventoryWriter;

    // 외부 서비스 검증은 Writer의 DB 트랜잭션이 시작되기 전에 수행한다.
    public InventoryResponse restock(
            UUID hubId,
            UUID productId,
            InventoryRestockRequest request,
            UUID userId,
            String role
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
                    "재고 입고는 MASTER만 가능합니다."
            );
        }
        if (request == null || request.getRestockRequestId() == null
                || request.getCompanyId() == null || request.getQuantity() == null
                || request.getQuantity() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "입고 요청 정보를 확인해주세요."
            );
        }

        companyValidator.validate(
                hubId,
                request.getCompanyId(),
                productId,
                userId,
                role
        );

        return inventoryWriter.restock(
                hubId,
                productId,
                request.getCompanyId(),
                request.getRestockRequestId(),
                request.getQuantity(),
                userId
        );
    }
}

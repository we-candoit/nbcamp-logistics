package logistics.delivery.presentation.util;

import logistics.delivery.common.exception.DeliveryErrorCode;
import logistics.delivery.common.exception.DeliveryException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class DeliveryPageableUtils {

    private DeliveryPageableUtils() {}

    /**
     * 페이지 범위와 정렬 입력을 검증하고 ID를 보조 정렬로 추가한다.
     *
     * @param page 0부터 시작하는 페이지 번호
     * @param size 1~100 사이의 페이지 크기
     * @param sort 생성일 또는 수정일과 정렬 방향
     * @return 검증된 페이징·정렬 정보
     */
    public static Pageable create(int page, int size, String sort) {
        if (page < 0) {
            throw new DeliveryException(DeliveryErrorCode.INVALID_PAGE);
        }

        if (size < 1 || size > 100) {
            throw new DeliveryException(DeliveryErrorCode.INVALID_PAGE_SIZE);
        }

        String[] parts = sort.split(",", -1);

        if (parts.length != 2) {
            throw new DeliveryException(DeliveryErrorCode.INVALID_SORT);
        }

        String property = parts[0].trim();
        String direction = parts[1].trim();

        if (!"createdAt".equals(property)
                && !"updatedAt".equals(property)) {
            throw new DeliveryException(DeliveryErrorCode.INVALID_SORT);
        }

        if (!"asc".equalsIgnoreCase(direction)
                && !"desc".equalsIgnoreCase(direction)) {
            throw new DeliveryException(DeliveryErrorCode.INVALID_SORT);
        }

        Sort ordering = Sort.by(
                Sort.Direction.fromString(direction),
                property
        ).and(Sort.by(Sort.Direction.DESC, "id"));

        return PageRequest.of(page, size, ordering);
    }
}

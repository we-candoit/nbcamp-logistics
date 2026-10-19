package logistics.delivery.presentation.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class DeliveryPageableUtils {

    private DeliveryPageableUtils() {}

    public static Pageable create(int page, int size, String sort) {
        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "페이지 번호는 0 이상이어야 합니다."
            );
        }

        if (size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "페이지 크기는 1~100이어야 합니다."
            );
        }

        String[] parts = sort.split(",", -1);

        if (parts.length != 2) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "정렬은 필드,방향 형식이어야 합니다."
            );
        }

        String property = parts[0].trim();
        String direction = parts[1].trim();

        if (!"createdAt".equals(property)
                && !"updatedAt".equals(property)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "정렬은 createdAt, updatedAt만 가능합니다."
            );
        }

        if (!"asc".equalsIgnoreCase(direction)
                && !"desc".equalsIgnoreCase(direction)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "정렬 방향은 asc, desc만 가능합니다."
            );
        }

        Sort ordering = Sort.by(
                Sort.Direction.fromString(direction),
                property
        ).and(Sort.by(Sort.Direction.DESC, "id"));

        return PageRequest.of(page, size, ordering);
    }
}

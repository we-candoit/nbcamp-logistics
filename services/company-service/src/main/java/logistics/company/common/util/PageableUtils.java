package logistics.company.common.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

public class PageableUtils {
    private static final List<Integer> ALLOWED_SIZES = List.of(10, 30, 50);     // 허용된 페이지 크기
    private static final int DEFAULT_SIZE = 10;                                 // 기본 페이지 크기 : 10

    private PageableUtils() {   // 객체를 만들어 쓸 일이 없는 유틸 클래스 private 지정
    }                           // PageableUtils.normalize(pageable)로 호출해서 사용

    public static Pageable normalize(Pageable pageable) {
        // 허용되지 않은 페이지 크기는 기본값 10으로 변경
        int size = ALLOWED_SIZES.contains(pageable.getPageSize())
                ? pageable.getPageSize()
                : DEFAULT_SIZE;

        return PageRequest.of(pageable.getPageNumber(), size, pageable.getSort());
    }
}

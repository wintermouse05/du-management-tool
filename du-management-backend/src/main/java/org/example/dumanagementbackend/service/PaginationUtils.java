package org.example.dumanagementbackend.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

final class PaginationUtils {

    private PaginationUtils() {
    }

    static Pageable toZeroBasedPageable(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return pageable;
        }

        int resolvedPage = Math.max(pageable.getPageNumber() - 1, 0);
        if (resolvedPage == pageable.getPageNumber()) {
            return pageable;
        }
        return PageRequest.of(resolvedPage, pageable.getPageSize(), pageable.getSort());
    }
}

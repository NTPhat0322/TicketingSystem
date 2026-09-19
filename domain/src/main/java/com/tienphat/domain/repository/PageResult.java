package com.tienphat.domain.repository;

import java.util.List;

/**
 * A slice of a larger, 0-indexed result set. {@code page}/{@code size} echo the
 * {@link PageRequest} that produced this slice.
 */
public record PageResult<T>(List<T> content, int page, int size, long totalElements) {

    public int totalPages() {
        if (size <= 0 || totalElements <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalElements / size);
    }

    public boolean hasNext() {
        return (long) (page + 1) * size < totalElements;
    }
}

package com.tienphat.domain.repository;

/**
 * A 0-indexed page request. {@code page == 0} is the first page.
 */
public record PageRequest(int page, int size) {

    public int offset() {
        return page * size;
    }
}

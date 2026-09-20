package com.tienphat.domain.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResultTest {

    @Test
    @DisplayName("totalPages() divides exactly when totalElements is a multiple of size")
    void totalPages_exactDivision() {
        PageResult<String> result = new PageResult<>(List.of(), 0, 5, 20);

        assertThat(result.totalPages()).isEqualTo(4);
    }

    @Test
    @DisplayName("totalPages() rounds up when there is a remainder")
    void totalPages_remainderRoundsUp() {
        PageResult<String> result = new PageResult<>(List.of(), 0, 5, 21);

        assertThat(result.totalPages()).isEqualTo(5);
    }

    @Test
    @DisplayName("totalPages() is zero when there are no elements")
    void totalPages_zeroTotalElements() {
        PageResult<String> result = new PageResult<>(List.of(), 0, 5, 0);

        assertThat(result.totalPages()).isZero();
    }

    @Test
    @DisplayName("hasNext() is false on the last page")
    void hasNext_falseOnLastPage() {
        PageResult<String> result = new PageResult<>(List.of(), 3, 5, 20);

        assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("hasNext() is true mid-list")
    void hasNext_trueMidList() {
        PageResult<String> result = new PageResult<>(List.of(), 1, 5, 20);

        assertThat(result.hasNext()).isTrue();
    }

    @Test
    @DisplayName("hasNext() is false when there are no elements")
    void hasNext_falseWhenNoElements() {
        PageResult<String> result = new PageResult<>(List.of(), 0, 5, 0);

        assertThat(result.hasNext()).isFalse();
    }
}

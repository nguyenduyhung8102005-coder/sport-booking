package com.sportbooking.common;

import com.sportbooking.exception.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageableUtilsTest {

    private static final List<String> ALLOWED = List.of("avgRating", "name");

    @Test
    void allowedSort_keepsPageAndSize_andAppendsIdTieBreaker() {
        Pageable input = PageRequest.of(2, 15, Sort.by(Sort.Direction.DESC, "avgRating"));

        Pageable result = PageableUtils.withAllowedSort(input, ALLOWED);

        assertThat(result.getPageNumber()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(15);
        assertThat(result.getSort().toList()).containsExactly(
                Sort.Order.desc("avgRating"),
                Sort.Order.asc("id"));
    }

    @Test
    void unsortedRequest_getsIdOrder() {
        Pageable result = PageableUtils.withAllowedSort(PageRequest.of(0, 10), ALLOWED);

        assertThat(result.getSort().toList()).containsExactly(Sort.Order.asc("id"));
    }

    @Test
    void sortByPropertyNotInWhitelist_isRejected() {
        Pageable input = PageRequest.of(0, 10, Sort.by("passwordHash"));

        assertThatThrownBy(() -> PageableUtils.withAllowedSort(input, ALLOWED))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("passwordHash");
    }
}

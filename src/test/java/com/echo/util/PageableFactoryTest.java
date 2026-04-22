package com.echo.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageableFactoryTest {

    private final PageableFactory pageableFactory = new PageableFactory();

    @Test
    void createsBoundedPageable() {
        var pageable = pageableFactory.create(1, 50);

        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(50);
    }

    @Test
    void rejectsInvalidPageAndSize() {
        assertThatThrownBy(() -> pageableFactory.create(-1, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pageableFactory.create(0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pageableFactory.create(0, 51))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

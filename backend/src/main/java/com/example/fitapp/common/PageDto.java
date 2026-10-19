package com.example.fitapp.common;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Page envelope shared by list endpoints. Size is capped at {@link #MAX_SIZE}. */
public record PageDto<T>(List<T> items, int page, int size, long total, boolean hasNext) {

    public static final int MAX_SIZE = 100;
    public static final int DEFAULT_SIZE = 50;

    public static <S, T> PageDto<T> of(Page<S> page, Function<S, T> mapper) {
        return new PageDto<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.hasNext());
    }

    public static int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_SIZE);
    }

    public static int clampPage(int page) {
        return Math.max(page, 0);
    }
}

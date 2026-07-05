package com.archipelago.dto.request;

import com.archipelago.exception.IllegalStateException;
import com.archipelago.model.enums.MovieGraphStatus;
import org.springframework.util.StringUtils;

public record MovieSearchCriteria(
        String query,
        String person,
        String genre,
        Integer releaseYear,
        MovieGraphStatus graphStatus,
        Long userId
) {
    public static MovieSearchCriteria from(
            String query,
            String person,
            String genre,
            String year,
            String graphStatus,
            Long userId
    ) {
        return new MovieSearchCriteria(
                clean(query),
                clean(person),
                clean(genre),
                parseYear(year),
                MovieGraphStatus.fromQuery(graphStatus),
                userId
        );
    }

    public boolean hasFilters() {
        return StringUtils.hasText(person)
                || StringUtils.hasText(genre)
                || releaseYear != null
                || graphStatus != MovieGraphStatus.ALL;
    }

    private static String clean(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static Integer parseYear(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (!trimmed.matches("\\d{4}")) {
            throw new IllegalStateException("Year must be a four-digit number");
        }
        return Integer.valueOf(trimmed);
    }
}

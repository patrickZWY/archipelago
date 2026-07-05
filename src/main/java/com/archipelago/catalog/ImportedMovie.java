package com.archipelago.catalog;

import com.archipelago.model.Movie;
import org.springframework.util.StringUtils;

import java.util.List;

public record ImportedMovie(
        String title,
        Integer releaseYear,
        String director,
        String pictureUrl,
        String externalId,
        String tagline,
        String synopsis,
        List<String> genres,
        Integer runtimeMinutes,
        List<String> castMembers,
        String directorNotes
) {
    public Movie toMovie() {
        return Movie.builder()
                .title(clean(title))
                .releaseYear(releaseYear)
                .director(clean(director))
                .pictureUrl(clean(pictureUrl))
                .externalId(clean(externalId))
                .tagline(clean(tagline))
                .synopsis(clean(synopsis))
                .genres(join(genres))
                .runtimeMinutes(runtimeMinutes)
                .castMembers(join(castMembers))
                .directorNotes(clean(directorNotes))
                .build();
    }

    public boolean hasRequiredMovieFields() {
        return StringUtils.hasText(title) && releaseYear != null && StringUtils.hasText(director);
    }

    public List<String> cleanGenres() {
        return cleanList(genres);
    }

    public List<String> cleanCastMembers() {
        return cleanList(castMembers);
    }

    private static String join(List<String> values) {
        List<String> cleaned = cleanList(values);
        if (cleaned.isEmpty()) {
            return null;
        }
        return String.join(", ", cleaned);
    }

    private static List<String> cleanList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(ImportedMovie::clean)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private static String clean(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}

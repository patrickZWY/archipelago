package com.archipelago.service.impl;

import com.archipelago.exception.ResourceNotFoundException;
import com.archipelago.mapper.MovieMapper;
import com.archipelago.model.Movie;
import com.archipelago.service.MovieService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class MovieServiceImpl implements MovieService {

    private final MovieMapper movieMapper;

    @Override
    public boolean movieExists(Long id) {
        return movieMapper.findById(id).isPresent();
    }

    @Override
    public Movie getMovieById(Long id) {
        return movieMapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found"));
    }

    @Override
    public List<Movie> searchMovies(String title) {
        List<Movie> catalog = movieMapper.findAll();
        if (!StringUtils.hasText(title)) {
            return catalog.stream().limit(25).toList();
        }

        String normalizedQuery = normalize(title);
        return catalog.stream()
                .map(movie -> new RankedMovie(movie, score(movie, normalizedQuery)))
                .filter(result -> result.score() > 0.18d)
                .sorted(Comparator
                        .comparingDouble(RankedMovie::score).reversed()
                        .thenComparing(result -> result.movie().getTitle()))
                .limit(25)
                .map(RankedMovie::movie)
                .toList();
    }

    private double score(Movie movie, String normalizedQuery) {
        String normalizedTitle = normalize(movie.getTitle());
        String normalizedDirector = normalize(movie.getDirector());

        if (normalizedTitle.equals(normalizedQuery)) {
            return 1.0d;
        }

        double containsScore = normalizedTitle.contains(normalizedQuery) ? 0.92d : 0.0d;
        double directorScore = normalizedDirector.contains(normalizedQuery) ? 0.45d : 0.0d;
        double tokenScore = normalizedTitle.split(" ").length > 1
                ? java.util.Arrays.stream(normalizedTitle.split(" "))
                .mapToDouble(token -> similarity(token, normalizedQuery) * 0.82d)
                .max()
                .orElse(0.0d)
                : 0.0d;
        double fullTitleScore = similarity(normalizedTitle, normalizedQuery);

        return Math.max(Math.max(containsScore, fullTitleScore), Math.max(tokenScore, directorScore));
    }

    private double similarity(String left, String right) {
        int maxLength = Math.max(left.length(), right.length());
        if (maxLength == 0) {
            return 1.0d;
        }
        int distance = levenshtein(left, right);
        return 1.0d - ((double) distance / maxLength);
    }

    private int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];

        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost
                );
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }

        return previous[right.length()];
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record RankedMovie(Movie movie, double score) {
    }
}

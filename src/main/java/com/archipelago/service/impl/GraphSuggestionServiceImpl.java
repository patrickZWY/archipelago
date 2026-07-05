package com.archipelago.service.impl;

import com.archipelago.dto.response.GraphSuggestionEvidenceResponse;
import com.archipelago.dto.response.GraphSuggestionResponse;
import com.archipelago.dto.response.MovieResponse;
import com.archipelago.exception.IllegalStateException;
import com.archipelago.exception.ResourceNotFoundException;
import com.archipelago.mapper.CatalogMetadataMapper;
import com.archipelago.mapper.ConnectionMapper;
import com.archipelago.mapper.MovieMapper;
import com.archipelago.model.Connection;
import com.archipelago.model.Movie;
import com.archipelago.model.enums.ConnectionCategory;
import com.archipelago.security.CurrentUserProvider;
import com.archipelago.service.GraphSuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GraphSuggestionServiceImpl implements GraphSuggestionService {
    private static final int DEFAULT_LIMIT = 8;
    private static final int MAX_LIMIT = 25;
    private static final Set<String> IMPLEMENTED_CATEGORIES = Set.of(
            ConnectionCategory.DIRECTOR.value(),
            ConnectionCategory.CAST.value(),
            ConnectionCategory.GENRE.value(),
            ConnectionCategory.ERA.value()
    );

    private final MovieMapper movieMapper;
    private final ConnectionMapper connectionMapper;
    private final CatalogMetadataMapper catalogMetadataMapper;
    private final CurrentUserProvider currentUserProvider;

    @Override
    public List<GraphSuggestionResponse> getSuggestions(
            Long movieId,
            Integer limit,
            String categories,
            boolean includeExisting
    ) {
        int boundedLimit = validateLimit(limit);
        Set<String> allowedCategories = parseCategories(categories);
        Movie rootMovie = movieMapper.findById(movieId)
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found"));
        MovieFacts rootFacts = factsFor(rootMovie);
        Long userId = currentUserProvider.getCurrentUser().getId();
        List<Connection> userConnections = connectionMapper.findByUserId(userId);

        return movieMapper.findAll().stream()
                .filter(candidate -> !candidate.getId().equals(movieId))
                .map(candidate -> buildSuggestion(rootFacts, factsFor(candidate), userConnections, includeExisting, allowedCategories))
                .filter(candidate -> candidate != null)
                .sorted(Comparator
                        .comparing(SuggestionCandidate::confidence).reversed()
                        .thenComparing(candidate -> candidate.response().candidateMovie().title())
                        .thenComparing(candidate -> candidate.response().candidateMovie().id()))
                .limit(boundedLimit)
                .map(SuggestionCandidate::response)
                .toList();
    }

    private SuggestionCandidate buildSuggestion(
            MovieFacts rootFacts,
            MovieFacts candidateFacts,
            List<Connection> userConnections,
            boolean includeExisting,
            Set<String> allowedCategories
    ) {
        boolean existingEdge = userConnections.stream()
                .anyMatch(connection -> connects(connection, rootFacts.movie().getId(), candidateFacts.movie().getId()));
        if (existingEdge && !includeExisting) {
            return null;
        }

        List<EvidenceScore> scores = new ArrayList<>();
        addSharedEvidence(
                scores,
                allowedCategories,
                ConnectionCategory.DIRECTOR.value(),
                "SHARED_DIRECTOR",
                "Shared director",
                rootFacts.directors(),
                candidateFacts.directors(),
                0.45
        );
        addSharedEvidence(
                scores,
                allowedCategories,
                ConnectionCategory.CAST.value(),
                "SHARED_CAST",
                "Shared cast",
                rootFacts.castMembers(),
                candidateFacts.castMembers(),
                0.10
        );
        addSharedEvidence(
                scores,
                allowedCategories,
                ConnectionCategory.GENRE.value(),
                "SHARED_GENRE",
                "Shared genre",
                rootFacts.genres(),
                candidateFacts.genres(),
                0.08
        );
        addEraEvidence(scores, allowedCategories, rootFacts, candidateFacts);

        if (scores.isEmpty()) {
            return null;
        }

        String category = scores.stream()
                .max(Comparator.comparing(EvidenceScore::score))
                .map(EvidenceScore::category)
                .orElse(ConnectionCategory.THEME.value());
        double confidence = Math.min(0.95, scores.stream().mapToDouble(EvidenceScore::score).sum());
        GraphSuggestionResponse response = new GraphSuggestionResponse(
                candidateFacts.response(),
                category,
                roundConfidence(confidence),
                scores.stream().map(EvidenceScore::evidence).toList(),
                existingEdge
        );
        return new SuggestionCandidate(response);
    }

    private void addSharedEvidence(
            List<EvidenceScore> scores,
            Set<String> allowedCategories,
            String category,
            String type,
            String label,
            Map<String, String> rootValues,
            Map<String, String> candidateValues,
            double perMatchScore
    ) {
        if (!allowedCategories.contains(category)) {
            return;
        }
        List<String> shared = rootValues.keySet().stream()
                .filter(candidateValues::containsKey)
                .map(rootValues::get)
                .toList();
        if (shared.isEmpty()) {
            return;
        }
        double score = Math.min(0.35, shared.size() * perMatchScore);
        if (ConnectionCategory.DIRECTOR.value().equals(category)) {
            score = 0.45;
        }
        scores.add(new EvidenceScore(
                category,
                score,
                new GraphSuggestionEvidenceResponse(type, label, shared)
        ));
    }

    private void addEraEvidence(
            List<EvidenceScore> scores,
            Set<String> allowedCategories,
            MovieFacts rootFacts,
            MovieFacts candidateFacts
    ) {
        if (!allowedCategories.contains(ConnectionCategory.ERA.value())) {
            return;
        }
        if (rootFacts.decade() == null || !rootFacts.decade().equals(candidateFacts.decade())) {
            return;
        }
        scores.add(new EvidenceScore(
                ConnectionCategory.ERA.value(),
                0.08,
                new GraphSuggestionEvidenceResponse("SAME_DECADE", "Same release era", List.of(rootFacts.decade() + "s"))
        ));
    }

    private MovieFacts factsFor(Movie movie) {
        MovieResponse response = MovieResponse.from(
                movie,
                catalogMetadataMapper.findGenresByMovieId(movie.getId()),
                catalogMetadataMapper.findPeopleByMovieId(movie.getId()),
                catalogMetadataMapper.findExternalIdsByMovieId(movie.getId())
        );
        Map<String, String> directors = new LinkedHashMap<>();
        addValue(directors, movie.getDirector());
        response.people().stream()
                .filter(person -> "DIRECTOR".equalsIgnoreCase(person.role()))
                .forEach(person -> addValue(directors, person.name()));

        Map<String, String> castMembers = new LinkedHashMap<>();
        response.castMembers().forEach(castMember -> addValue(castMembers, castMember));

        Map<String, String> genres = new LinkedHashMap<>();
        response.genres().forEach(genre -> addValue(genres, genre));

        Integer decade = movie.getReleaseYear() > 0 ? (movie.getReleaseYear() / 10) * 10 : null;
        return new MovieFacts(movie, response, directors, castMembers, genres, decade);
    }

    private void addValue(Map<String, String> values, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        values.putIfAbsent(value.trim().toLowerCase(Locale.ROOT), value.trim());
    }

    private boolean connects(Connection connection, Long firstMovieId, Long secondMovieId) {
        Long fromMovieId = connection.getFromMovie().getId();
        Long toMovieId = connection.getToMovie().getId();
        return (fromMovieId.equals(firstMovieId) && toMovieId.equals(secondMovieId))
                || (fromMovieId.equals(secondMovieId) && toMovieId.equals(firstMovieId));
    }

    private int validateLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalStateException("Limit must be between 1 and 25");
        }
        return limit;
    }

    private Set<String> parseCategories(String categories) {
        if (!StringUtils.hasText(categories)) {
            return IMPLEMENTED_CATEGORIES;
        }
        Set<String> parsed = new LinkedHashSet<>();
        Arrays.stream(categories.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .forEach(category -> parsed.add(ConnectionCategory.fromValue(category)
                        .map(ConnectionCategory::value)
                        .orElseThrow(() -> new IllegalStateException("Categories must be one or more supported graph categories"))));
        return parsed.isEmpty() ? IMPLEMENTED_CATEGORIES : parsed;
    }

    private double roundConfidence(double confidence) {
        return Math.round(confidence * 100.0) / 100.0;
    }

    private record MovieFacts(
            Movie movie,
            MovieResponse response,
            Map<String, String> directors,
            Map<String, String> castMembers,
            Map<String, String> genres,
            Integer decade
    ) {
    }

    private record EvidenceScore(
            String category,
            double score,
            GraphSuggestionEvidenceResponse evidence
    ) {
    }

    private record SuggestionCandidate(GraphSuggestionResponse response) {
        double confidence() {
            return response.confidence();
        }
    }
}

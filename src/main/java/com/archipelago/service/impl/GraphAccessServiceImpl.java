package com.archipelago.service.impl;

import com.archipelago.dto.response.GlobalGraphConnectionResponse;
import com.archipelago.dto.response.GlobalGraphPathResponse;
import com.archipelago.dto.response.GlobalGraphResponse;
import com.archipelago.dto.response.ConnectionResponse;
import com.archipelago.dto.response.MovieConnectionsResponse;
import com.archipelago.dto.response.MoviePathResponse;
import com.archipelago.dto.response.MovieResponse;
import com.archipelago.dto.response.PublicUserResponse;
import com.archipelago.exception.IllegalStateException;
import com.archipelago.exception.ResourceNotFoundException;
import com.archipelago.mapper.ConnectionMapper;
import com.archipelago.mapper.FriendshipMapper;
import com.archipelago.mapper.MovieMapper;
import com.archipelago.model.Connection;
import com.archipelago.model.Movie;
import com.archipelago.model.User;
import com.archipelago.service.GraphAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class GraphAccessServiceImpl implements GraphAccessService {

    private final ConnectionMapper connectionMapper;
    private final MovieMapper movieMapper;
    private final FriendshipMapper friendshipMapper;

    @Override
    public MovieConnectionsResponse getMovieGraph(Long ownerUserId, Long rootMovieId) {
        Movie rootMovie = movieMapper.findById(rootMovieId)
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found"));
        List<Connection> allConnections = connectionMapper.findByUserId(ownerUserId);
        List<Connection> componentConnections = getComponentConnections(rootMovieId, allConnections);
        Map<Long, MovieResponse> componentMovies = new LinkedHashMap<>();
        componentMovies.put(rootMovie.getId(), MovieResponse.from(rootMovie));
        for (Connection connection : componentConnections) {
            componentMovies.putIfAbsent(connection.getFromMovie().getId(), MovieResponse.from(connection.getFromMovie()));
            componentMovies.putIfAbsent(connection.getToMovie().getId(), MovieResponse.from(connection.getToMovie()));
        }
        return new MovieConnectionsResponse(
                MovieResponse.from(rootMovie),
                componentMovies.values().stream().toList(),
                componentConnections.stream().map(ConnectionResponse::from).toList()
        );
    }

    @Override
    public MoviePathResponse getShortestPath(Long ownerUserId, Long fromMovieId, Long toMovieId, String disconnectedMessage) {
        Movie fromMovie = movieMapper.findById(fromMovieId)
                .orElseThrow(() -> new ResourceNotFoundException("Source movie not found"));
        Movie toMovie = movieMapper.findById(toMovieId)
                .orElseThrow(() -> new ResourceNotFoundException("Target movie not found"));

        if (Objects.equals(fromMovieId, toMovieId)) {
            return new MoviePathResponse(
                    MovieResponse.from(fromMovie),
                    MovieResponse.from(toMovie),
                    List.of(MovieResponse.from(fromMovie)),
                    List.of()
            );
        }

        List<Connection> allConnections = connectionMapper.findByUserId(ownerUserId);
        if (allConnections.isEmpty()) {
            throw new IllegalStateException(disconnectedMessage);
        }

        GraphPathResult<Connection> path = findShortestPath(
                fromMovieId,
                toMovieId,
                allConnections,
                connection -> connection.getFromMovie().getId(),
                connection -> connection.getToMovie().getId(),
                disconnectedMessage
        );
        List<MovieResponse> pathMovies = path.movieIds().stream()
                .map(movieId -> MovieResponse.from(Objects.equals(movieId, fromMovieId)
                        ? fromMovie
                        : Objects.equals(movieId, toMovieId)
                        ? toMovie
                        : getMovieFromConnectionsOrFallback(movieId, allConnections, fromMovie)))
                .toList();
        List<ConnectionResponse> pathConnections = path.connections().stream()
                .map(ConnectionResponse::from)
                .toList();

        return new MoviePathResponse(
                MovieResponse.from(fromMovie),
                MovieResponse.from(toMovie),
                pathMovies,
                pathConnections
        );
    }

    @Override
    public GlobalGraphResponse getFullGraph(Long ownerUserId) {
        List<Connection> connections = connectionMapper.findByUserId(ownerUserId);
        return new GlobalGraphResponse(
                buildMovieResponses(connections),
                connections.stream()
                        .map(GlobalGraphConnectionResponse::from)
                        .toList()
        );
    }

    @Override
    public GlobalGraphResponse getMergedFriendGraph(Long viewerUserId) {
        List<Long> ownerUserIds = new ArrayList<>();
        ownerUserIds.add(viewerUserId);
        friendshipMapper.findAcceptedForUser(viewerUserId).stream()
                .map(friendship -> friendship.getRequester().getId().equals(viewerUserId)
                        ? friendship.getRecipient()
                        : friendship.getRequester())
                .map(User::getId)
                .distinct()
                .forEach(ownerUserIds::add);

        List<Connection> connections = connectionMapper.findByUserIds(ownerUserIds);
        return new GlobalGraphResponse(
                buildMovieResponses(connections),
                mergeConnections(connections)
        );
    }

    @Override
    public GlobalGraphPathResponse getFullGraphShortestPath(Long ownerUserId, Long fromMovieId, Long toMovieId, String disconnectedMessage) {
        Movie fromMovie = movieMapper.findById(fromMovieId)
                .orElseThrow(() -> new ResourceNotFoundException("Source movie not found"));
        Movie toMovie = movieMapper.findById(toMovieId)
                .orElseThrow(() -> new ResourceNotFoundException("Target movie not found"));
        List<Connection> connections = connectionMapper.findByUserId(ownerUserId);
        if (connections.isEmpty()) {
            throw new IllegalStateException(disconnectedMessage);
        }

        GraphPathResult<Connection> path = findShortestPath(
                fromMovieId,
                toMovieId,
                connections,
                connection -> connection.getFromMovie().getId(),
                connection -> connection.getToMovie().getId(),
                disconnectedMessage
        );

        return new GlobalGraphPathResponse(
                MovieResponse.from(fromMovie),
                MovieResponse.from(toMovie),
                path.movieIds().stream()
                        .map(movieId -> MovieResponse.from(Objects.equals(movieId, fromMovieId)
                                ? fromMovie
                                : Objects.equals(movieId, toMovieId)
                                ? toMovie
                                : getMovieFromConnectionsOrFallback(movieId, connections, fromMovie)))
                        .toList(),
                path.connections().stream().map(GlobalGraphConnectionResponse::from).toList()
        );
    }

    @Override
    public GlobalGraphPathResponse getMergedFriendGraphShortestPath(Long viewerUserId, Long fromMovieId, Long toMovieId, String disconnectedMessage) {
        Movie fromMovie = movieMapper.findById(fromMovieId)
                .orElseThrow(() -> new ResourceNotFoundException("Source movie not found"));
        Movie toMovie = movieMapper.findById(toMovieId)
                .orElseThrow(() -> new ResourceNotFoundException("Target movie not found"));
        GlobalGraphResponse graph = getMergedFriendGraph(viewerUserId);
        if (graph.connections().isEmpty()) {
            throw new IllegalStateException(disconnectedMessage);
        }

        GraphPathResult<GlobalGraphConnectionResponse> path = findShortestPath(
                fromMovieId,
                toMovieId,
                graph.connections(),
                GlobalGraphConnectionResponse::fromMovieId,
                GlobalGraphConnectionResponse::toMovieId,
                disconnectedMessage
        );

        Map<Long, MovieResponse> moviesById = new LinkedHashMap<>();
        for (MovieResponse movie : graph.movies()) {
            moviesById.put(movie.id(), movie);
        }
        moviesById.putIfAbsent(fromMovieId, MovieResponse.from(fromMovie));
        moviesById.putIfAbsent(toMovieId, MovieResponse.from(toMovie));

        return new GlobalGraphPathResponse(
                MovieResponse.from(fromMovie),
                MovieResponse.from(toMovie),
                path.movieIds().stream().map(movieId -> moviesById.getOrDefault(movieId, MovieResponse.from(fromMovie))).toList(),
                path.connections()
        );
    }

    @Override
    public List<Movie> getGraphMovies(Long ownerUserId) {
        return movieMapper.findDistinctByUserId(ownerUserId);
    }

    private List<MovieResponse> buildMovieResponses(List<Connection> connections) {
        Map<Long, MovieResponse> movies = new LinkedHashMap<>();
        for (Connection connection : connections) {
            movies.putIfAbsent(connection.getFromMovie().getId(), MovieResponse.from(connection.getFromMovie()));
            movies.putIfAbsent(connection.getToMovie().getId(), MovieResponse.from(connection.getToMovie()));
        }
        return movies.values().stream()
                .sorted(Comparator.comparing(MovieResponse::title).thenComparing(MovieResponse::id))
                .toList();
    }

    private List<GlobalGraphConnectionResponse> mergeConnections(List<Connection> connections) {
        Map<String, List<Connection>> groupedConnections = new LinkedHashMap<>();
        for (Connection connection : connections) {
            Long lowerMovieId = Math.min(connection.getFromMovie().getId(), connection.getToMovie().getId());
            Long higherMovieId = Math.max(connection.getFromMovie().getId(), connection.getToMovie().getId());
            groupedConnections.computeIfAbsent(lowerMovieId + ":" + higherMovieId, ignored -> new ArrayList<>())
                    .add(connection);
        }

        List<GlobalGraphConnectionResponse> mergedConnections = new ArrayList<>();
        long syntheticId = 1L;
        for (List<Connection> group : groupedConnections.values()) {
            Connection representative = group.get(0);
            Map<Long, PublicUserResponse> contributorMap = new LinkedHashMap<>();
            for (Connection connection : group) {
                User user = connection.getUser();
                if (user != null) {
                    contributorMap.putIfAbsent(user.getId(), PublicUserResponse.from(user));
                }
            }
            List<PublicUserResponse> contributors = contributorMap.values().stream()
                    .sorted(Comparator.comparing(PublicUserResponse::username).thenComparing(PublicUserResponse::id))
                    .toList();
            double maxWeight = group.stream().map(Connection::getWeight).max(Double::compareTo).orElse(1.0);
            String mergedCategory = pickMergedCategory(group);
            mergedConnections.add(new GlobalGraphConnectionResponse(
                    syntheticId++,
                    representative.getFromMovie().getId(),
                    representative.getFromMovie().getTitle(),
                    representative.getToMovie().getId(),
                    representative.getToMovie().getTitle(),
                    "Merged from " + contributors.size() + " graph" + (contributors.size() == 1 ? "" : "s"),
                    maxWeight,
                    mergedCategory,
                    true,
                    contributors,
                    contributors.size()
            ));
        }

        return mergedConnections;
    }

    private String pickMergedCategory(List<Connection> group) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Connection connection : group) {
            String category = connection.getCategory();
            if (category == null || category.isBlank()) {
                continue;
            }
            counts.merge(category, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private List<Connection> getComponentConnections(Long movieId, List<Connection> allConnections) {
        if (allConnections.isEmpty()) {
            return List.of();
        }

        Set<Long> connectedMovieIds = new HashSet<>();
        Queue<Long> pendingMovieIds = new ArrayDeque<>();
        connectedMovieIds.add(movieId);
        pendingMovieIds.add(movieId);

        while (!pendingMovieIds.isEmpty()) {
            Long currentMovieId = pendingMovieIds.remove();
            for (Connection connection : allConnections) {
                Long fromConnectionMovieId = connection.getFromMovie().getId();
                Long toConnectionMovieId = connection.getToMovie().getId();
                if (!fromConnectionMovieId.equals(currentMovieId) && !toConnectionMovieId.equals(currentMovieId)) {
                    continue;
                }

                if (connectedMovieIds.add(fromConnectionMovieId)) {
                    pendingMovieIds.add(fromConnectionMovieId);
                }
                if (connectedMovieIds.add(toConnectionMovieId)) {
                    pendingMovieIds.add(toConnectionMovieId);
                }
            }
        }

        List<Connection> componentConnections = new ArrayList<>();
        for (Connection connection : allConnections) {
            Long fromConnectionMovieId = connection.getFromMovie().getId();
            Long toConnectionMovieId = connection.getToMovie().getId();
            if (connectedMovieIds.contains(fromConnectionMovieId) && connectedMovieIds.contains(toConnectionMovieId)) {
                componentConnections.add(connection);
            }
        }
        return componentConnections;
    }

    private Map<Long, List<Connection>> buildAdjacency(List<Connection> connections) {
        Map<Long, List<Connection>> adjacency = new HashMap<>();
        for (Connection connection : connections) {
            adjacency.computeIfAbsent(connection.getFromMovie().getId(), ignored -> new ArrayList<>()).add(connection);
            adjacency.computeIfAbsent(connection.getToMovie().getId(), ignored -> new ArrayList<>()).add(connection);
        }
        return adjacency;
    }

    private Movie getMovieFromConnectionsOrFallback(Long movieId, List<Connection> connections, Movie fallbackMovie) {
        for (Connection connection : connections) {
            if (Objects.equals(connection.getFromMovie().getId(), movieId)) {
                return connection.getFromMovie();
            }
            if (Objects.equals(connection.getToMovie().getId(), movieId)) {
                return connection.getToMovie();
            }
        }
        return fallbackMovie;
    }

    private <T> GraphPathResult<T> findShortestPath(
            Long fromMovieId,
            Long toMovieId,
            List<T> connections,
            java.util.function.Function<T, Long> fromMovieIdAccessor,
            java.util.function.Function<T, Long> toMovieIdAccessor,
            String disconnectedMessage
    ) {
        if (Objects.equals(fromMovieId, toMovieId)) {
            return new GraphPathResult<>(List.of(fromMovieId), List.of());
        }

        Map<Long, List<T>> adjacency = buildAdjacency(connections, fromMovieIdAccessor, toMovieIdAccessor);
        Queue<Long> pendingMovieIds = new ArrayDeque<>();
        Map<Long, Long> previousMovieIds = new HashMap<>();
        Map<Long, T> previousConnections = new HashMap<>();
        Set<Long> visitedMovieIds = new HashSet<>();

        pendingMovieIds.add(fromMovieId);
        visitedMovieIds.add(fromMovieId);

        while (!pendingMovieIds.isEmpty()) {
            Long currentMovieId = pendingMovieIds.remove();
            if (Objects.equals(currentMovieId, toMovieId)) {
                break;
            }
            for (T connection : adjacency.getOrDefault(currentMovieId, List.of())) {
                Long connectionFromMovieId = fromMovieIdAccessor.apply(connection);
                Long connectionToMovieId = toMovieIdAccessor.apply(connection);
                Long neighborMovieId = Objects.equals(connectionFromMovieId, currentMovieId)
                        ? connectionToMovieId
                        : connectionFromMovieId;
                if (!visitedMovieIds.add(neighborMovieId)) {
                    continue;
                }
                previousMovieIds.put(neighborMovieId, currentMovieId);
                previousConnections.put(neighborMovieId, connection);
                pendingMovieIds.add(neighborMovieId);
            }
        }

        if (!previousMovieIds.containsKey(toMovieId)) {
            throw new IllegalStateException(disconnectedMessage);
        }

        List<Long> movieIds = new ArrayList<>();
        List<T> pathConnections = new ArrayList<>();
        Long currentMovieId = toMovieId;
        movieIds.add(currentMovieId);
        while (!Objects.equals(currentMovieId, fromMovieId)) {
            T connection = previousConnections.get(currentMovieId);
            pathConnections.add(0, connection);
            currentMovieId = previousMovieIds.get(currentMovieId);
            movieIds.add(0, currentMovieId);
        }

        return new GraphPathResult<>(movieIds, pathConnections);
    }

    private <T> Map<Long, List<T>> buildAdjacency(
            List<T> connections,
            java.util.function.Function<T, Long> fromMovieIdAccessor,
            java.util.function.Function<T, Long> toMovieIdAccessor
    ) {
        Map<Long, List<T>> adjacency = new HashMap<>();
        for (T connection : connections) {
            adjacency.computeIfAbsent(fromMovieIdAccessor.apply(connection), ignored -> new ArrayList<>()).add(connection);
            adjacency.computeIfAbsent(toMovieIdAccessor.apply(connection), ignored -> new ArrayList<>()).add(connection);
        }
        return adjacency;
    }

    private record GraphPathResult<T>(List<Long> movieIds, List<T> connections) {
    }
}

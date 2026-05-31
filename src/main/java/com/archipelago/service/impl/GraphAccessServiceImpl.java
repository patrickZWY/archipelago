package com.archipelago.service.impl;

import com.archipelago.dto.response.ConnectionResponse;
import com.archipelago.dto.response.MovieConnectionsResponse;
import com.archipelago.dto.response.MoviePathResponse;
import com.archipelago.dto.response.MovieResponse;
import com.archipelago.exception.IllegalStateException;
import com.archipelago.exception.ResourceNotFoundException;
import com.archipelago.mapper.ConnectionMapper;
import com.archipelago.mapper.MovieMapper;
import com.archipelago.model.Connection;
import com.archipelago.model.Movie;
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

@Service
@RequiredArgsConstructor
public class GraphAccessServiceImpl implements GraphAccessService {

    private final ConnectionMapper connectionMapper;
    private final MovieMapper movieMapper;

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

        Map<Long, List<Connection>> adjacency = buildAdjacency(allConnections);
        Queue<Long> pendingMovieIds = new ArrayDeque<>();
        Map<Long, Long> previousMovieIds = new HashMap<>();
        Map<Long, Connection> previousConnections = new HashMap<>();
        Set<Long> visitedMovieIds = new HashSet<>();

        pendingMovieIds.add(fromMovieId);
        visitedMovieIds.add(fromMovieId);

        while (!pendingMovieIds.isEmpty()) {
            Long currentMovieId = pendingMovieIds.remove();
            if (Objects.equals(currentMovieId, toMovieId)) {
                break;
            }
            for (Connection connection : adjacency.getOrDefault(currentMovieId, List.of())) {
                Long neighborMovieId = Objects.equals(connection.getFromMovie().getId(), currentMovieId)
                        ? connection.getToMovie().getId()
                        : connection.getFromMovie().getId();
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

        List<MovieResponse> pathMovies = new ArrayList<>();
        List<ConnectionResponse> pathConnections = new ArrayList<>();
        Long currentMovieId = toMovieId;
        pathMovies.add(MovieResponse.from(getMovieFromConnectionsOrFallback(currentMovieId, allConnections, toMovie)));
        while (!Objects.equals(currentMovieId, fromMovieId)) {
            Connection connection = previousConnections.get(currentMovieId);
            pathConnections.add(0, ConnectionResponse.from(connection));
            currentMovieId = previousMovieIds.get(currentMovieId);
            Movie movie = Objects.equals(currentMovieId, fromMovieId)
                    ? fromMovie
                    : getMovieFromConnectionsOrFallback(currentMovieId, allConnections, fromMovie);
            pathMovies.add(0, MovieResponse.from(movie));
        }

        return new MoviePathResponse(
                MovieResponse.from(fromMovie),
                MovieResponse.from(toMovie),
                pathMovies,
                pathConnections
        );
    }

    @Override
    public List<Movie> getGraphMovies(Long ownerUserId) {
        return movieMapper.findDistinctByUserId(ownerUserId);
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
}

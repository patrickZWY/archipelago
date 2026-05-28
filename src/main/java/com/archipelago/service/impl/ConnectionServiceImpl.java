package com.archipelago.service.impl;

import com.archipelago.dto.request.CreateConnectionRequest;
import com.archipelago.dto.request.UpdateConnectionRequest;
import com.archipelago.exception.IllegalStateException;
import com.archipelago.exception.ResourceNotFoundException;
import com.archipelago.mapper.ConnectionMapper;
import com.archipelago.mapper.MovieMapper;
import com.archipelago.model.Connection;
import com.archipelago.model.Movie;
import com.archipelago.model.User;
import com.archipelago.security.CurrentUserProvider;
import com.archipelago.service.ConnectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ConnectionServiceImpl implements ConnectionService {

    private final ConnectionMapper connectionMapper;
    private final MovieMapper movieMapper;
    private final CurrentUserProvider currentUserProvider;

    @Override
    public List<Connection> getConnectionsForCurrentUser() {
        return connectionMapper.findByUserId(currentUserProvider.getCurrentUser().getId());
    }

    @Override
    public List<Connection> getConnectionsForCurrentUserByMovieComponent(Long movieId) {
        User user = currentUserProvider.getCurrentUser();
        movieMapper.findById(movieId).orElseThrow(() -> new ResourceNotFoundException("Movie not found"));
        List<Connection> allConnections = connectionMapper.findByUserId(user.getId());
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
                Long fromMovieId = connection.getFromMovie().getId();
                Long toMovieId = connection.getToMovie().getId();
                if (!fromMovieId.equals(currentMovieId) && !toMovieId.equals(currentMovieId)) {
                    continue;
                }

                if (connectedMovieIds.add(fromMovieId)) {
                    pendingMovieIds.add(fromMovieId);
                }
                if (connectedMovieIds.add(toMovieId)) {
                    pendingMovieIds.add(toMovieId);
                }
            }
        }

        List<Connection> componentConnections = new ArrayList<>();
        for (Connection connection : allConnections) {
            Long fromMovieId = connection.getFromMovie().getId();
            Long toMovieId = connection.getToMovie().getId();
            if (connectedMovieIds.contains(fromMovieId) && connectedMovieIds.contains(toMovieId)) {
                componentConnections.add(connection);
            }
        }
        return componentConnections;
    }

    @Override
    public Connection createConnection(CreateConnectionRequest request) {
        if (request.fromMovieId().equals(request.toMovieId())) {
            throw new IllegalStateException("A connection must reference two different movies");
        }
        User user = currentUserProvider.getCurrentUser();
        Movie fromMovie = movieMapper.findById(request.fromMovieId())
                .orElseThrow(() -> new ResourceNotFoundException("Source movie not found"));
        Movie toMovie = movieMapper.findById(request.toMovieId())
                .orElseThrow(() -> new ResourceNotFoundException("Target movie not found"));

        Connection connection = Connection.builder()
                .fromMovie(fromMovie)
                .toMovie(toMovie)
                .reason(request.reason().trim())
                .weight(request.weight() == null ? 1.0 : request.weight())
                .category(request.category() == null ? null : request.category().trim())
                .user(user)
                .build();
        connectionMapper.insert(connection);
        return connectionMapper.findByIdAndUserId(connection.getId(), user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Connection was not created"));
    }

    @Override
    public Connection updateConnection(Long connectionId, UpdateConnectionRequest request) {
        User user = currentUserProvider.getCurrentUser();
        Connection connection = connectionMapper.findByIdAndUserId(connectionId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Connection not found"));
        connection.setUser(user);
        connection.setReason(request.reason().trim());
        connection.setWeight(request.weight() == null ? connection.getWeight() : request.weight());
        connection.setCategory(request.category() == null ? null : request.category().trim());
        connectionMapper.update(connection);
        return connectionMapper.findByIdAndUserId(connectionId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Connection not found"));
    }

    @Override
    public void deleteConnection(Long connectionId) {
        User user = currentUserProvider.getCurrentUser();
        connectionMapper.findByIdAndUserId(connectionId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Connection not found"));
        connectionMapper.deleteByIdAndUserId(connectionId, user.getId());
    }
}

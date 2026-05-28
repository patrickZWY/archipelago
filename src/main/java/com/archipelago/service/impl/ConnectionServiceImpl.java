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

import java.util.List;

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
    public List<Connection> getConnectionsForCurrentUserByMovie(Long movieId) {
        User user = currentUserProvider.getCurrentUser();
        movieMapper.findById(movieId).orElseThrow(() -> new ResourceNotFoundException("Movie not found"));
        return connectionMapper.findByUserIdAndMovieId(user.getId(), movieId);
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

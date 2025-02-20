package com.archipelago.service.impl;

import com.archipelago.exception.ResourceNotFoundException;
import com.archipelago.mapper.ConnectionMapper;
import com.archipelago.mapper.MovieMapper;
import com.archipelago.model.Connection;
import com.archipelago.model.Movie;
import com.archipelago.model.User;
import com.archipelago.repository.ConnectionRepository;
import com.archipelago.repository.MovieRepository;
import com.archipelago.service.ConnectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConnectionServiceImpl implements ConnectionService {
    private final ConnectionMapper connectionMapper;
    private final MovieMapper movieMapper;

    @Override
    public Connection addConnection(User user, Long fromMovieId, Long toMovieId, String reason) {
        Movie fromMovie = movieMapper.findById(fromMovieId)
                .orElseThrow(() -> new ResourceNotFoundException("From movie not found"));
        Movie toMovie = movieMapper.findById(toMovieId)
                .orElseThrow(() -> new ResourceNotFoundException("To movie not found"));

        Connection connection = Connection.builder()
                .fromMovie(fromMovie)
                .toMovie(toMovie)
                .reason(reason)
                .user(user)
                .build();
        connectionMapper.insert(connection);
        return connection;
    }

    @Override
    public List<Connection> getConnectionsByUser(User user) {
        return connectionMapper.findByUserId(user.getId());
    }

    @Override
    public void deleteConnection(Long connectionId) {
        connectionMapper.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Connection not found"));
        connectionMapper.delete(connectionId);
    }

    @Override
    public Connection updateConnection(Long connectionId, String newReason) {
        Connection connection = connectionMapper.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Connection not found"));
        connection.setReason(newReason);
        connectionMapper.update(connection);
        return connection;
    }
}


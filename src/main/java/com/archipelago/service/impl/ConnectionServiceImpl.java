package com.archipelago.service.impl;

import com.archipelago.exception.ResourceNotFoundException;
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
    private final ConnectionRepository connectionRepository;
    private final MovieRepository movieRepository;

    @Override
    public Connection addConnection(User user, Long fromMovieId, Long toMovieId, String reason) {
        Movie fromMovie = movieRepository.findById(fromMovieId)
                .orElseThrow(() -> new ResourceNotFoundException("From movie not found"));
        Movie toMovie = movieRepository.findById(toMovieId)
                .orElseThrow(() -> new ResourceNotFoundException("To movie not found"));

        Connection connection = Connection.builder()
                .fromMovie(fromMovie)
                .toMovie(toMovie)
                .reason(reason)
                .user(user)
                .build();
        return connectionRepository.save(connection);
    }

    @Override
    public List<Connection> getConnectionsByUser(User user) {
        return connectionRepository.findByUser(user);
    }

    @Override
    public void deleteConnection(Long connectionId) {
        Connection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Connection not found"));
        connectionRepository.delete(connection);
    }

    @Override
    public Connection updateConnection(Long connectionId, String newReason) {
        Connection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Connection not found"));
        connection.setReason(newReason);
        return connectionRepository.save(connection);
    }
}


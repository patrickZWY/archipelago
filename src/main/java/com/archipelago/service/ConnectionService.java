package com.archipelago.service;

import com.archipelago.exception.ResourceNotFoundException;
import com.archipelago.model.Connection;
import com.archipelago.model.Movie;
import com.archipelago.model.User;
import com.archipelago.repository.ConnectionRepository;
import com.archipelago.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConnectionService {
    private final ConnectionRepository connectionRepository;
    private final MovieRepository movieRepository;

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

    public List<Connection> getConnectionsByUser(User user) {
        return connectionRepository.findByUser(user);
    }

    public void deleteConnection(Long connectionId) {
        Connection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Connection not found"));
        connectionRepository.delete(connection);
    }

    public Connection updateConnection(Long connectionId, String newReason) {
        Connection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Connection not found"));
        connection.setReason(newReason);
        return connectionRepository.save(connection);
    }

}

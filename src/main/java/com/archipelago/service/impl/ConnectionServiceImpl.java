package com.archipelago.service.impl;

import com.archipelago.exception.ResourceNotFoundException;
import com.archipelago.mapper.ConnectionMapper;
import com.archipelago.mapper.MovieMapper;
import com.archipelago.model.Connection;
import com.archipelago.model.Movie;
import com.archipelago.model.User;
import com.archipelago.service.ConnectionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConnectionServiceImpl implements ConnectionService {
    private final Logger logger = LoggerFactory.getLogger(ConnectionServiceImpl.class);
    private final ConnectionMapper connectionMapper;
    private final MovieMapper movieMapper;

    @Override
    public Connection addConnection(User user, Long fromMovieId, Long toMovieId, String reason) {
        logger.info("Adding connection for user {} from {} to {}", user.getId(), fromMovieId, toMovieId);
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
        logger.info("Added connection for user {} from {} to {}", user.getId(), fromMovieId, toMovieId);
        return connection;
    }

    @Override
    public List<Connection> getConnectionsByUser(User user) {
        logger.info("Getting connections for user {}", user.getId());
        List<Connection> connections = connectionMapper.findByUserId(user.getId());
        logger.info("Found {} connections", connections.size());
        return connections;
    }

    @Override
    public void deleteConnection(Long connectionId) {
        logger.info("Deleting connection with id {}", connectionId);
        connectionMapper.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Connection not found"));
        connectionMapper.delete(connectionId);
        logger.info("Deleted connection with id {}", connectionId);
    }

    @Override
    public Connection updateConnection(Long connectionId, String newReason) {
        logger.info("Updating connection with id {}", connectionId);
        Connection connection = connectionMapper.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Connection not found"));
        connection.setReason(newReason);
        connectionMapper.update(connection);
        logger.info("Updated connection with id {}", connectionId);
        return connection;
    }
}


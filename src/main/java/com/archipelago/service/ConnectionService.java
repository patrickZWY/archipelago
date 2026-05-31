package com.archipelago.service;

import com.archipelago.dto.request.CreateConnectionRequest;
import com.archipelago.dto.request.UpdateConnectionRequest;
import com.archipelago.dto.response.MoviePathResponse;
import com.archipelago.model.Connection;

import java.util.List;

public interface ConnectionService {
    List<Connection> getConnectionsForCurrentUser();
    List<Connection> getConnectionsForCurrentUserByMovieComponent(Long movieId);
    MoviePathResponse getShortestPathForCurrentUser(Long fromMovieId, Long toMovieId);
    Connection createConnection(CreateConnectionRequest request);
    Connection updateConnection(Long connectionId, UpdateConnectionRequest request);
    void deleteConnection(Long connectionId);
}

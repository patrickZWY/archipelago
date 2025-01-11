package com.archipelago.service;

import com.archipelago.model.Connection;
import com.archipelago.model.User;

import java.util.List;

public interface ConnectionService {
    Connection addConnection(User user, Long fromMovieId, Long toMovieId, String reason);
    List<Connection> getConnectionsByUser(User user);
    void deleteConnection(Long connectionId);
    Connection updateConnection(Long connectionId, String newReason);
}


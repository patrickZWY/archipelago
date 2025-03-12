package com.archipelago.controller;

import com.archipelago.auth.AuthController;
import com.archipelago.model.Connection;
import com.archipelago.model.User;
import com.archipelago.service.ConnectionService;
import com.archipelago.util.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/connections")
@RequiredArgsConstructor
public class ConnectionController {

    private final ConnectionService connectionService;
    private static final Logger logger = LoggerFactory.getLogger(ConnectionController.class);

    @GetMapping
    public ResponseEntity<ApiResponse<List<Connection>>> getConnections(@RequestAttribute User user) {
        logger.info("Get connections for {}", user.getUsername());
        List<Connection> connections = connectionService.getConnectionsByUser(user);
        logger.info("Found {} connections", connections.size());
        return ResponseEntity.ok(ApiResponse.success(connections, "Connections retrieved success"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Connection>> addConnection(@RequestAttribute User user,
                                                                 @RequestParam Long fromMovieId,
                                                                 @RequestParam Long toMovieId,
                                                                 @RequestParam String reason) {
        logger.info("Add connection for {}", user.getUsername());
        Connection connection = connectionService.addConnection(user, fromMovieId, toMovieId, reason);
        logger.info("Added connection for {}", user.getUsername());
        return ResponseEntity.ok(ApiResponse.success(connection, "Connection added success"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Connection>> updateConnection(@PathVariable("id") Long id, @RequestParam String reason) {
        logger.info("Update connection for {}", id);
        Connection connection = connectionService.updateConnection(id, reason);
        logger.info("Updated connection for {}", id);
        return ResponseEntity.ok(ApiResponse.success(connection, "Connection updated success"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteConnection(@PathVariable Long id) {
        logger.info("Delete connection for {}", id);
        connectionService.deleteConnection(id);
        logger.info("Deleted connection for {}", id);
        return ResponseEntity.ok(ApiResponse.success("Connection deleted success"));
    }


}

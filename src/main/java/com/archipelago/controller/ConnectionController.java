package com.archipelago.controller;

import com.archipelago.model.Connection;
import com.archipelago.model.User;
import com.archipelago.service.ConnectionService;
import com.archipelago.util.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/connections")
@RequiredArgsConstructor
public class ConnectionController {

    private final ConnectionService connectionService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Connection>>> getConnections(@RequestAttribute User user) {
        List<Connection> connections = connectionService.getConnectionsByUser(user);
        return ResponseEntity.ok(ApiResponse.success(connections, "Connections retrieved success"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Connection>> addConnection(@RequestAttribute User user,
                                                                 @RequestParam Long fromMovieId,
                                                                 @RequestParam Long toMovieId,
                                                                 @RequestParam String reason) {
        Connection connection = connectionService.addConnection(user, fromMovieId, toMovieId, reason);
        return ResponseEntity.ok(ApiResponse.success(connection, "Connection added success"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Connection>> updateConnection(@PathVariable("id") Long id, @RequestParam String reason) {
        Connection connection = connectionService.updateConnection(id, reason);
        return ResponseEntity.ok(ApiResponse.success(connection, "Connection updated success"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteConnection(@PathVariable Long id) {
        connectionService.deleteConnection(id);
        return ResponseEntity.ok(ApiResponse.success("Connection deleted success"));
    }


}

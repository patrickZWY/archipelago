package com.archipelago.controller;

import com.archipelago.dto.request.CreateConnectionRequest;
import com.archipelago.dto.request.UpdateConnectionRequest;
import com.archipelago.dto.response.ConnectionResponse;
import com.archipelago.service.ConnectionService;
import com.archipelago.util.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/connections")
@RequiredArgsConstructor
public class ConnectionController {

    private final ConnectionService connectionService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ConnectionResponse>>> getConnections() {
        List<ConnectionResponse> connections = connectionService.getConnectionsForCurrentUser().stream()
                .map(ConnectionResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(connections, "Connections retrieved"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ConnectionResponse>> createConnection(@Valid @RequestBody CreateConnectionRequest request) {
        ConnectionResponse response = ConnectionResponse.from(connectionService.createConnection(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Connection created"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ConnectionResponse>> updateConnection(
            @PathVariable Long id,
            @Valid @RequestBody UpdateConnectionRequest request
    ) {
        ConnectionResponse response = ConnectionResponse.from(connectionService.updateConnection(id, request));
        return ResponseEntity.ok(ApiResponse.success(response, "Connection updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteConnection(@PathVariable Long id) {
        connectionService.deleteConnection(id);
        return ResponseEntity.ok(ApiResponse.success("Connection deleted"));
    }
}

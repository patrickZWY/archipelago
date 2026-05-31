package com.archipelago.controller;

import com.archipelago.dto.request.CreateSharedGraphExportRequest;
import com.archipelago.dto.response.SharedGraphExportResponse;
import com.archipelago.dto.response.SharedGraphResponse;
import com.archipelago.service.SharedGraphService;
import com.archipelago.util.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shares")
@RequiredArgsConstructor
public class SharedGraphController {

    private final SharedGraphService sharedGraphService;

    @PostMapping
    public ResponseEntity<ApiResponse<SharedGraphExportResponse>> createShare(
            @Valid @RequestBody CreateSharedGraphExportRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(sharedGraphService.createExport(request), "Shared graph created"));
    }

    @GetMapping("/{shareToken}")
    public ResponseEntity<ApiResponse<SharedGraphResponse>> getSharedGraph(@PathVariable String shareToken) {
        return ResponseEntity.ok(ApiResponse.success(
                sharedGraphService.getSharedGraph(shareToken),
                "Shared graph retrieved"
        ));
    }
}

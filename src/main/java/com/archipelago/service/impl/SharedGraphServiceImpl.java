package com.archipelago.service.impl;

import com.archipelago.dto.request.CreateSharedGraphExportRequest;
import com.archipelago.dto.response.SharedGraphExportResponse;
import com.archipelago.dto.response.SharedGraphExportSummaryResponse;
import com.archipelago.dto.response.SharedGraphResponse;
import com.archipelago.exception.ResourceNotFoundException;
import com.archipelago.mapper.MovieMapper;
import com.archipelago.mapper.SharedGraphExportMapper;
import com.archipelago.model.Movie;
import com.archipelago.model.SharedGraphExport;
import com.archipelago.security.CurrentUserProvider;
import com.archipelago.service.GraphAccessService;
import com.archipelago.service.SharedGraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SharedGraphServiceImpl implements SharedGraphService {

    private final SharedGraphExportMapper sharedGraphExportMapper;
    private final MovieMapper movieMapper;
    private final CurrentUserProvider currentUserProvider;
    private final GraphAccessService graphAccessService;

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    @Override
    public SharedGraphExportResponse createExport(CreateSharedGraphExportRequest request) {
        Movie movie = movieMapper.findById(request.movieId())
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found"));

        SharedGraphExport export = SharedGraphExport.builder()
                .shareToken(UUID.randomUUID().toString().replace("-", ""))
                .ownerUserId(currentUserProvider.getCurrentUser().getId())
                .rootMovieId(movie.getId())
                .title(request.title() == null || request.title().isBlank() ? movie.getTitle() + " graph" : request.title().trim())
                .build();
        sharedGraphExportMapper.insert(export);

        return new SharedGraphExportResponse(
                export.getShareToken(),
                frontendBaseUrl + "/shared/" + export.getShareToken(),
                export.getTitle(),
                graphAccessService.getMovieGraph(export.getOwnerUserId(), export.getRootMovieId())
        );
    }

    @Override
    public List<SharedGraphExportSummaryResponse> listExports() {
        Long currentUserId = currentUserProvider.getCurrentUser().getId();
        return sharedGraphExportMapper.findByOwnerUserId(currentUserId).stream()
                .map(export -> new SharedGraphExportSummaryResponse(
                        export.getShareToken(),
                        frontendBaseUrl + "/shared/" + export.getShareToken(),
                        export.getTitle(),
                        export.getRootMovieId(),
                        movieMapper.findById(export.getRootMovieId())
                                .map(Movie::getTitle)
                                .orElse("Unknown movie"),
                        export.getCreationTime()
                ))
                .toList();
    }

    @Override
    public void revokeExport(String shareToken) {
        Long currentUserId = currentUserProvider.getCurrentUser().getId();
        int deleted = sharedGraphExportMapper.deleteByShareTokenAndOwnerUserId(shareToken, currentUserId);
        if (deleted == 0) {
            throw new ResourceNotFoundException("Shared graph not found");
        }
    }

    @Override
    public SharedGraphResponse getSharedGraph(String shareToken) {
        SharedGraphExport export = sharedGraphExportMapper.findByShareToken(shareToken)
                .orElseThrow(() -> new ResourceNotFoundException("Shared graph not found"));
        return new SharedGraphResponse(
                export.getShareToken(),
                export.getTitle(),
                graphAccessService.getMovieGraph(export.getOwnerUserId(), export.getRootMovieId())
        );
    }
}

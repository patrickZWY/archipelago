package com.archipelago.service;

import com.archipelago.dto.response.GlobalGraphPathResponse;
import com.archipelago.dto.response.GlobalGraphResponse;
import com.archipelago.dto.response.MovieConnectionsResponse;
import com.archipelago.dto.response.MoviePathResponse;
import com.archipelago.model.Movie;

import java.util.List;

public interface GraphAccessService {
    MovieConnectionsResponse getMovieGraph(Long ownerUserId, Long rootMovieId);

    MoviePathResponse getShortestPath(Long ownerUserId, Long fromMovieId, Long toMovieId, String disconnectedMessage);

    GlobalGraphResponse getFullGraph(Long ownerUserId);

    GlobalGraphResponse getMergedFriendGraph(Long viewerUserId);

    GlobalGraphPathResponse getFullGraphShortestPath(Long ownerUserId, Long fromMovieId, Long toMovieId, String disconnectedMessage);

    GlobalGraphPathResponse getMergedFriendGraphShortestPath(Long viewerUserId, Long fromMovieId, Long toMovieId, String disconnectedMessage);

    List<Movie> getGraphMovies(Long ownerUserId);
}

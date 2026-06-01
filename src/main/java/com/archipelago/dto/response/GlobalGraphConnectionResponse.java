package com.archipelago.dto.response;

import com.archipelago.model.Connection;

import java.util.List;

public record GlobalGraphConnectionResponse(
        Long id,
        Long fromMovieId,
        String fromMovieTitle,
        Long toMovieId,
        String toMovieTitle,
        String reason,
        Double weight,
        String category,
        boolean aggregate,
        List<PublicUserResponse> contributors,
        int contributorCount
) {

    public static GlobalGraphConnectionResponse from(Connection connection) {
        return new GlobalGraphConnectionResponse(
                connection.getId(),
                connection.getFromMovie().getId(),
                connection.getFromMovie().getTitle(),
                connection.getToMovie().getId(),
                connection.getToMovie().getTitle(),
                connection.getReason(),
                connection.getWeight(),
                connection.getCategory(),
                false,
                List.of(),
                0
        );
    }
}

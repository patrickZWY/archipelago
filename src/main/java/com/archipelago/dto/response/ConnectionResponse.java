package com.archipelago.dto.response;

import com.archipelago.model.Connection;

public record ConnectionResponse(
        Long id,
        Long fromMovieId,
        String fromMovieTitle,
        Long toMovieId,
        String toMovieTitle,
        String reason,
        Double weight,
        String category
) {

    public static ConnectionResponse from(Connection connection) {
        return new ConnectionResponse(
                connection.getId(),
                connection.getFromMovie().getId(),
                connection.getFromMovie().getTitle(),
                connection.getToMovie().getId(),
                connection.getToMovie().getTitle(),
                connection.getReason(),
                connection.getWeight(),
                connection.getCategory()
        );
    }
}

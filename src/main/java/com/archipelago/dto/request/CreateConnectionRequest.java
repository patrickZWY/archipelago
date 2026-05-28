package com.archipelago.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateConnectionRequest(
        @NotNull(message = "From movie id is required") Long fromMovieId,
        @NotNull(message = "To movie id is required") Long toMovieId,
        @NotBlank(message = "Reason is required") @Size(max = 500, message = "Reason must be at most 500 characters") String reason,
        @DecimalMin(value = "0.0", inclusive = false, message = "Weight must be greater than zero") Double weight,
        @Size(max = 255, message = "Category must be at most 255 characters") String category
) {
}

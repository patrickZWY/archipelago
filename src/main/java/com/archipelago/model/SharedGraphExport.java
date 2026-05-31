package com.archipelago.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SharedGraphExport {
    private Long id;
    private String shareToken;
    private Long ownerUserId;
    private Long rootMovieId;
    private String title;
    private LocalDateTime creationTime;
}

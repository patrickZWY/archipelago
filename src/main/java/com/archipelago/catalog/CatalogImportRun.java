package com.archipelago.catalog;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CatalogImportRun {
    private String id;
    private String providerId;
    private String source;
    private String operation;
    private String status;
    private Integer insertedCount;
    private Integer updatedCount;
    private Integer skippedCount;
    private Integer failedCount;
    private Integer totalProcessed;
    private String errorKind;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Long durationMs;
}

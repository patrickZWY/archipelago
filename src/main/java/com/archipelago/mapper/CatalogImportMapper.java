package com.archipelago.mapper;

import com.archipelago.catalog.CatalogImportRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface CatalogImportMapper {
    void insertRun(CatalogImportRun run);

    void completeRun(
            @Param("id") String id,
            @Param("source") String source,
            @Param("status") String status,
            @Param("insertedCount") int insertedCount,
            @Param("updatedCount") int updatedCount,
            @Param("skippedCount") int skippedCount,
            @Param("failedCount") int failedCount,
            @Param("totalProcessed") int totalProcessed,
            @Param("errorKind") String errorKind,
            @Param("errorMessage") String errorMessage,
            @Param("completedAt") LocalDateTime completedAt,
            @Param("durationMs") long durationMs
    );

    Long findMovieIdByProviderExternalId(
            @Param("providerId") String providerId,
            @Param("externalIdType") String externalIdType,
            @Param("externalId") String externalId
    );

    void deleteExternalIdsForMovieSource(
            @Param("movieId") Long movieId,
            @Param("providerId") String providerId,
            @Param("source") String source
    );

    void deleteExternalIdByProviderExternalId(
            @Param("providerId") String providerId,
            @Param("externalIdType") String externalIdType,
            @Param("externalId") String externalId
    );

    void insertExternalId(
            @Param("movieId") Long movieId,
            @Param("providerId") String providerId,
            @Param("source") String source,
            @Param("externalIdType") String externalIdType,
            @Param("externalId") String externalId
    );

    void deleteGenresForMovieSource(
            @Param("movieId") Long movieId,
            @Param("providerId") String providerId,
            @Param("source") String source
    );

    void insertGenre(
            @Param("movieId") Long movieId,
            @Param("providerId") String providerId,
            @Param("source") String source,
            @Param("genre") String genre
    );

    void deletePeopleForMovieSource(
            @Param("movieId") Long movieId,
            @Param("providerId") String providerId,
            @Param("source") String source
    );

    void insertPerson(
            @Param("movieId") Long movieId,
            @Param("providerId") String providerId,
            @Param("source") String source,
            @Param("personName") String personName,
            @Param("role") String role,
            @Param("billingOrder") int billingOrder
    );
}

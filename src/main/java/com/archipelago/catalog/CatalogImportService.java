package com.archipelago.catalog;

import com.archipelago.dto.response.CatalogImportResponse;
import com.archipelago.mapper.CatalogImportMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CatalogImportService {
    private static final Logger logger = LoggerFactory.getLogger(CatalogImportService.class);

    private final CatalogImportMapper catalogImportMapper;
    private final Map<String, CatalogProvider> providersById;

    public CatalogImportService(CatalogImportMapper catalogImportMapper, List<CatalogProvider> catalogProviders) {
        this.catalogImportMapper = catalogImportMapper;
        this.providersById = catalogProviders.stream()
                .collect(Collectors.toUnmodifiableMap(
                        provider -> provider.providerId().toLowerCase(Locale.ROOT),
                        Function.identity()
                ));
    }

    public CatalogImportResponse previewImport(String providerId, String source) {
        return execute(CatalogImportOperation.PREVIEW, providerId, source);
    }

    public CatalogImportResponse applyImport(String providerId, String source) {
        return execute(CatalogImportOperation.APPLY, providerId, source);
    }

    private CatalogImportResponse execute(CatalogImportOperation operation, String providerId, String source) {
        String resolvedProviderId = resolveProviderId(providerId);
        String runId = UUID.randomUUID().toString();
        String requestedSource = StringUtils.hasText(source) ? source.trim() : "default";
        LocalDateTime startedAt = LocalDateTime.now();
        Instant start = Instant.now();
        catalogImportMapper.insertRun(CatalogImportRun.builder()
                .id(runId)
                .providerId(resolvedProviderId)
                .source(requestedSource)
                .operation(operation.name())
                .status(CatalogImportRunStatus.STARTED.name())
                .startedAt(startedAt)
                .build());

        try {
            CatalogProvider provider = resolveProvider(resolvedProviderId);
            requireCapability(provider, CatalogCapability.MOVIE_METADATA);
            CatalogImportRequest request = new CatalogImportRequest(provider.providerId(), source, runId);
            CatalogImportResult result = operation == CatalogImportOperation.PREVIEW
                    ? provider.previewImport(request)
                    : provider.applyImport(request);

            long durationMs = Duration.between(start, Instant.now()).toMillis();
            CatalogErrorKind firstErrorKind = result.firstErrorKind();
            CatalogImportRunStatus status = result.failedCount() > 0
                    ? CatalogImportRunStatus.COMPLETED_WITH_FAILURES
                    : CatalogImportRunStatus.SUCCEEDED;
            catalogImportMapper.completeRun(
                    runId,
                    result.source(),
                    status.name(),
                    result.insertedCount(),
                    result.updatedCount(),
                    result.skippedCount(),
                    result.failedCount(),
                    result.totalProcessed(),
                    firstErrorKind == null ? null : firstErrorKind.name(),
                    firstErrorKind == null ? null : "One or more catalog items failed",
                    LocalDateTime.now(),
                    durationMs
            );
            logger.info(
                    "catalog_import operation={} provider={} source={} run_id={} inserted={} updated={} skipped={} failed={} total={} duration_ms={} error_kind={}",
                    operation,
                    result.provider(),
                    result.source(),
                    runId,
                    result.insertedCount(),
                    result.updatedCount(),
                    result.skippedCount(),
                    result.failedCount(),
                    result.totalProcessed(),
                    durationMs,
                    firstErrorKind
            );
            return CatalogImportResponse.from(result);
        } catch (CatalogException exception) {
            completeFailedRun(operation, resolvedProviderId, runId, requestedSource, start, exception.getErrorKind(), exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            CatalogException wrapped = new CatalogException(
                    CatalogErrorKind.PERMANENT_PROVIDER_DATA_ERROR,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Catalog provider failed to process the import",
                    exception
            );
            completeFailedRun(operation, resolvedProviderId, runId, requestedSource, start, wrapped.getErrorKind(), wrapped.getMessage());
            throw wrapped;
        }
    }

    private String resolveProviderId(String providerId) {
        return StringUtils.hasText(providerId) ? providerId.trim() : CuratedCatalogProvider.PROVIDER_ID;
    }

    private CatalogProvider resolveProvider(String providerId) {
        String resolvedProviderId = StringUtils.hasText(providerId) ? providerId.trim() : CuratedCatalogProvider.PROVIDER_ID;
        CatalogProvider provider = providersById.get(resolvedProviderId.toLowerCase(Locale.ROOT));
        if (provider == null) {
            throw new CatalogException(
                    CatalogErrorKind.PROVIDER_UNAVAILABLE,
                    HttpStatus.NOT_FOUND,
                    "Catalog provider is unavailable"
            );
        }
        return provider;
    }

    private void requireCapability(CatalogProvider provider, CatalogCapability capability) {
        if (!provider.capabilities().contains(capability)) {
            throw new CatalogException(
                    CatalogErrorKind.UNSUPPORTED_PROVIDER_CAPABILITY,
                    HttpStatus.BAD_REQUEST,
                    "Catalog provider does not support the requested capability"
            );
        }
    }

    private void completeFailedRun(
            CatalogImportOperation operation,
            String provider,
            String runId,
            String source,
            Instant start,
            CatalogErrorKind errorKind,
            String errorMessage
    ) {
        long durationMs = Duration.between(start, Instant.now()).toMillis();
        catalogImportMapper.completeRun(
                runId,
                source,
                CatalogImportRunStatus.FAILED.name(),
                0,
                0,
                0,
                0,
                0,
                errorKind.name(),
                errorMessage,
                LocalDateTime.now(),
                durationMs
        );
        logger.warn(
                "catalog_import operation={} provider={} source={} run_id={} duration_ms={} error_kind={}",
                operation,
                provider,
                source,
                runId,
                durationMs,
                errorKind
        );
    }
}

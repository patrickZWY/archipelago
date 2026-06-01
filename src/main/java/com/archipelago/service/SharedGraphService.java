package com.archipelago.service;

import com.archipelago.dto.request.CreateSharedGraphExportRequest;
import com.archipelago.dto.response.SharedGraphExportResponse;
import com.archipelago.dto.response.SharedGraphExportSummaryResponse;
import com.archipelago.dto.response.SharedGraphResponse;

import java.util.List;

public interface SharedGraphService {
    SharedGraphExportResponse createExport(CreateSharedGraphExportRequest request);

    List<SharedGraphExportSummaryResponse> listExports();

    void revokeExport(String shareToken);

    SharedGraphResponse getSharedGraph(String shareToken);
}

package com.archipelago.service;

import com.archipelago.dto.request.CreateSharedGraphExportRequest;
import com.archipelago.dto.response.SharedGraphExportResponse;
import com.archipelago.dto.response.SharedGraphResponse;

public interface SharedGraphService {
    SharedGraphExportResponse createExport(CreateSharedGraphExportRequest request);

    SharedGraphResponse getSharedGraph(String shareToken);
}

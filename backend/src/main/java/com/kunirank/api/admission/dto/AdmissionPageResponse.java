package com.kunirank.api.admission.dto;

import java.util.List;

public record AdmissionPageResponse(
        Integer selectedYear, List<AdmissionResultResponse> items,
        int page, int size, long totalCount, int totalPages, boolean hasNext
) {}

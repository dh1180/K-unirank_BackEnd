package com.kunirank.api.admission.controller;

import com.kunirank.api.admission.dto.AdmissionPageResponse;
import com.kunirank.api.admission.service.AdmissionQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admissions")
public class AdmissionController {
    private final AdmissionQueryService admissions;

    public AdmissionController(AdmissionQueryService admissions) {
        this.admissions = admissions;
    }

    @GetMapping("/results")
    public AdmissionPageResponse search(
            @RequestParam(required = false) @Min(1) Integer year,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String phase,
            @RequestParam(required = false) String track,
            @RequestParam(defaultValue = "public", name = "metrics") String metricsMode,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "60") @Min(1) @Max(100) int size
    ) {
        return admissions.search(year, q, kind, phase, track, metricsMode, page, size);
    }

    @GetMapping("/units/{id}")
    public AdmissionPageResponse unit(
            @PathVariable @Min(1) long id,
            @RequestParam(required = false) @Min(1) Integer year,
            @RequestParam(defaultValue = "public", name = "metrics") String metricsMode,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "60") @Min(1) @Max(100) int size
    ) {
        return admissions.forUnit(id, year, metricsMode, page, size);
    }
}

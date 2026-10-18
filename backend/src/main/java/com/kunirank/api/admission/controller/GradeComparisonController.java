package com.kunirank.api.admission.controller;

import com.kunirank.api.admission.dto.GradeComparisonResponse;
import com.kunirank.api.admission.service.GradeComparisonService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admissions/compare")
public class GradeComparisonController {
    private final GradeComparisonService comparisons;

    public GradeComparisonController(GradeComparisonService comparisons) {
        this.comparisons = comparisons;
    }

    @GetMapping
    public GradeComparisonResponse compare(
            @RequestParam @NotNull @DecimalMin("1.0") @DecimalMax("9.0") BigDecimal grade,
            @RequestParam(required = false) @Min(1) Integer year,
            @RequestParam(defaultValue = "student") String track,
            @RequestParam(defaultValue = "exclude") String special,
            @RequestParam(defaultValue = "near") String scope,
            @RequestParam(required = false, name = "chart_min") Integer chartMin,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String region,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "60") @Min(1) @Max(100) int size
    ) {
        return comparisons.compare(grade, year, track, special, scope, chartMin, q, region, page, size);
    }
}

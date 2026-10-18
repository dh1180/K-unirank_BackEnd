package com.kunirank.api.university.controller;

import com.kunirank.api.university.dto.UniversityPageResponse;
import com.kunirank.api.university.service.UniversityService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.kunirank.api.university.dto.UniversityDetailResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/universities")
public class UniversityController {

    private final UniversityService universityService;

    public UniversityController(UniversityService universityService) {
        this.universityService = universityService;
    }

    @GetMapping("/{id}")
    public UniversityDetailResponse detail(@PathVariable @Min(1) Long id) {
        return universityService.findUniversity(id);
    }

    @GetMapping
    public UniversityPageResponse list(
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(required = false) String region,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return universityService.findActiveUniversities(query, region, page, size);
    }
}

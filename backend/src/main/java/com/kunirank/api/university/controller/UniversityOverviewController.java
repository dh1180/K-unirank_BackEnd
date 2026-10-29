package com.kunirank.api.university.controller;

import com.kunirank.api.university.service.UniversityOverviewService;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/universities")
public class UniversityOverviewController {
    private final UniversityOverviewService overview;
    public UniversityOverviewController(UniversityOverviewService overview) { this.overview = overview; }
    @GetMapping("/{id}/overview")
    public UniversityOverviewService.Overview overview(@PathVariable @Min(1) long id) {
        return overview.overview(id);
    }
}

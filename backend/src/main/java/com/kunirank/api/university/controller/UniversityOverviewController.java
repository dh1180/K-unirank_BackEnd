package com.kunirank.api.university.controller;

import com.kunirank.api.university.service.UniversityOverviewService;
import com.kunirank.api.university.service.AdmissionReferenceService;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/universities")
public class UniversityOverviewController {
    private final UniversityOverviewService overview;
    private final AdmissionReferenceService reference;
    public UniversityOverviewController(UniversityOverviewService overview,AdmissionReferenceService reference) { this.overview = overview;this.reference=reference; }
    @GetMapping("/{id}/admission-reference")
    public AdmissionReferenceService.Reference reference(@PathVariable @Min(1) long id) {return reference.reference(id);}
    @GetMapping("/{id}/overview")
    public UniversityOverviewService.Overview overview(@PathVariable @Min(1) long id) {
        return overview.overview(id);
    }
}

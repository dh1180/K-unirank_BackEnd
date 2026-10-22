package com.kunirank.api.admission.entity;

import com.kunirank.api.university.entity.University;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "admission_sources")
public class AdmissionSource {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "source_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "university_id", nullable = false)
    private University university;

    @Column(name = "admission_year", nullable = false)
    private int admissionYear;

    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType;

    @Column(name = "source_url", nullable = false, length = 1000)
    private String sourceUrl;

    @Column(name = "document_title", nullable = false, length = 300)
    private String documentTitle = "";

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    @Column(name = "checksum", nullable = false, length = 64)
    private String checksum = "";

    @PrePersist
    private void setCollectionTime() {
        if (collectedAt == null) collectedAt = Instant.now();
    }

    protected AdmissionSource() {}

    public AdmissionSource(University university, int admissionYear, String sourceType, String sourceUrl) {
        this.university = university;
        this.admissionYear = admissionYear;
        this.sourceType = sourceType;
        this.sourceUrl = sourceUrl;
    }

    public Long getId() { return id; }
    public String getSourceType() { return sourceType; }
    public String getSourceUrl() { return sourceUrl; }
}

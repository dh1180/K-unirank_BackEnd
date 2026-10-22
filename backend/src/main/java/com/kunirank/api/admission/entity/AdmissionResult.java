package com.kunirank.api.admission.entity;

import com.kunirank.api.university.entity.University;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "admission_results")
public class AdmissionResult {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "result_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private AdmissionSource source;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "university_id", nullable = false)
    private University university;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recruitment_unit_id", nullable = false)
    private RecruitmentUnit unit;

    @Column(name = "admission_year", nullable = false)
    private int admissionYear;

    @Column(name = "admission_phase", nullable = false, length = 10)
    private String phase;

    @Column(name = "selection_category", nullable = false, length = 100)
    private String selectionCategory = "";

    @Column(name = "selection_name", nullable = false, length = 200)
    private String selectionName = "";

    @Column(name = "recruitment_group", nullable = false, length = 30)
    private String recruitmentGroup = "";

    @Column(name = "recruitment_count")
    private Integer recruitmentCount;

    @Column(name = "competition_rate", precision = 10, scale = 3)
    private BigDecimal competitionRate;

    protected AdmissionResult() {}

    public AdmissionResult(AdmissionSource source, University university, RecruitmentUnit unit,
                           int year, String phase, String category, String selectionName) {
        this.source = source;
        this.university = university;
        this.unit = unit;
        this.admissionYear = year;
        this.phase = phase;
        this.selectionCategory = category;
        this.selectionName = selectionName;
    }

    public Long getId() { return id; }
    public AdmissionSource getSource() { return source; }
    public University getUniversity() { return university; }
    public RecruitmentUnit getUnit() { return unit; }
    public int getAdmissionYear() { return admissionYear; }
    public String getPhase() { return phase; }
    public String getSelectionCategory() { return selectionCategory; }
    public String getSelectionName() { return selectionName; }
    public Integer getRecruitmentCount() { return recruitmentCount; }
    public BigDecimal getCompetitionRate() { return competitionRate; }
}

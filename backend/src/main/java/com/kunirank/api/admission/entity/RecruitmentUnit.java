package com.kunirank.api.admission.entity;

import com.kunirank.api.university.entity.University;
import jakarta.persistence.*;

@Entity
@Table(name = "recruitment_units")
public class RecruitmentUnit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recruitment_unit_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "university_id", nullable = false)
    private University university;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "college_name", nullable = false, length = 200)
    private String collegeName = "";

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected RecruitmentUnit() {}

    public RecruitmentUnit(University university, String name, String collegeName, boolean active) {
        this.university = university;
        this.name = name;
        this.collegeName = collegeName;
        this.active = active;
    }

    public Long getId() { return id; }
    public University getUniversity() { return university; }
    public String getName() { return name; }
    public String getCollegeName() { return collegeName; }
    public boolean isActive() { return active; }
}

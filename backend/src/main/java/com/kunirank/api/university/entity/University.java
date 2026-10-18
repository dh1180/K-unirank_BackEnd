package com.kunirank.api.university.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "universities")
public class University {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "university_id")
    private Long id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "short_name", length = 100)
    private String shortName;

    @Column(name = "region", length = 100)
    private String region;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "logo_path", length = 255)
    private String logoPath;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected University() {
        // Required by JPA.
    }

    public University(String name, String region, boolean active) {
        this.name = name;
        this.region = region;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getShortName() {
        return shortName;
    }

    public String getAddress() { return address; }

    public String getRegion() {
        return region;
    }

    public String getLogoPath() {
        return logoPath;
    }

    public boolean isActive() {
        return active;
    }
}

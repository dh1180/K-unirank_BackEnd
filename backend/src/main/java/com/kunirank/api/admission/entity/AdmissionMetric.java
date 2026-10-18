package com.kunirank.api.admission.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "admission_metrics")
public class AdmissionMetric {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "metric_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "result_id", nullable = false)
    private AdmissionResult result;

    @Column(name = "metric_code", nullable = false, length = 100)
    private String metricCode;

    @Column(name = "unit", nullable = false, length = 50)
    private String unit = "";

    @Column(name = "value", nullable = false, precision = 14, scale = 5)
    private BigDecimal value;

    protected AdmissionMetric() {}

    public AdmissionMetric(AdmissionResult result, String metricCode, BigDecimal value, String unit) {
        this.result = result;
        this.metricCode = metricCode;
        this.value = value;
        this.unit = unit;
    }

    public Long getId() { return id; }
    public AdmissionResult getResult() { return result; }
    public String getMetricCode() { return metricCode; }
    public String getUnit() { return unit; }
    public BigDecimal getValue() { return value; }
}

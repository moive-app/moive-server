package com.moive.MoiveBE.domain.recommendation.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "recommended_area")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendedArea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommended_area_id")
    private Long id;

    @Column(name = "recommendation_run_id", nullable = false)
    private Long recommendationRunId;

    @Column(name = "area_name", nullable = false)
    private String areaName;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private RecommendedArea(
            Long recommendationRunId,
            String areaName
    ) {
        this.recommendationRunId = recommendationRunId;
        this.areaName = areaName;
    }

    public static RecommendedArea create(
            Long recommendationRunId,
            String areaName
    ) {
        return new RecommendedArea(
                recommendationRunId,
                areaName
        );
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
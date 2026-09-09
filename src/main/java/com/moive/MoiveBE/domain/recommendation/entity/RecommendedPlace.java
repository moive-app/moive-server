package com.moive.MoiveBE.domain.recommendation.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendedPlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommended_place_id")
    private Long id;

    @Column(name = "recommended_area_id", nullable = false)
    private Long recommendedAreaId;

    @Column(name = "google_place_id", nullable = false)
    private String googlePlaceId;

    @Column(name = "preference_match_cnt", nullable = false)
    private Integer preferenceMatchCnt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private RecommendedPlace(
            Long recommendedAreaId,
            String googlePlaceId,
            Integer preferenceMatchCnt
    ) {
        this.recommendedAreaId = recommendedAreaId;
        this.googlePlaceId = googlePlaceId;
        this.preferenceMatchCnt = preferenceMatchCnt;
    }

    public static RecommendedPlace create(
            Long recommendedAreaId,
            String googlePlaceId,
            Integer preferenceMatchCnt
    ) {
        return new RecommendedPlace(
                recommendedAreaId,
                googlePlaceId,
                preferenceMatchCnt
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
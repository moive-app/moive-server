package com.moive.MoiveBE.domain.recommendation.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "recommendation_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommendation_run_id")
    private Long id;

    @Column(name = "meeting_id", nullable = false, unique = true)
    private Long meetingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecommendationStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

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

    public static RecommendationRun create(Long meetingId) {
        RecommendationRun run = new RecommendationRun();
        run.meetingId = meetingId;
        run.status = RecommendationStatus.PENDING;
        return run;
    }

    public void complete() {
        this.status = RecommendationStatus.COMPLETED;
    }

    public void fail() {
        this.status = RecommendationStatus.FAILED;
    }
}
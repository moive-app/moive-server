package com.moive.MoiveBE.domain.recommendation.repository;

import com.moive.MoiveBE.domain.recommendation.entity.RecommendationRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationRunRepository
        extends JpaRepository<RecommendationRun, Long> {
}
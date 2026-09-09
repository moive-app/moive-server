package com.moive.MoiveBE.domain.recommendation.repository;

import com.moive.MoiveBE.domain.recommendation.entity.RecommendedArea;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecommendedAreaRepository
        extends JpaRepository<RecommendedArea, Long> {

    List<RecommendedArea> findAllByRecommendationRunId(
            Long recommendationRunId
    );
}
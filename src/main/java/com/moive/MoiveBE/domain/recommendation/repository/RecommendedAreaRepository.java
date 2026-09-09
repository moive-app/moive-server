package com.moive.MoiveBE.domain.recommendation.repository;

import com.moive.MoiveBE.domain.recommendation.entity.RecommendedArea;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendedAreaRepository
        extends JpaRepository<RecommendedArea, Long> {
}
package com.moive.MoiveBE.domain.recommendation.repository;

import com.moive.MoiveBE.domain.recommendation.entity.RecommendedPlace;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendedPlaceRepository
        extends JpaRepository<RecommendedPlace, Long> {
}
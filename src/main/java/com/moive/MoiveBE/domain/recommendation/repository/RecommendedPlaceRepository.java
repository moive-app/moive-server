package com.moive.MoiveBE.domain.recommendation.repository;

import com.moive.MoiveBE.domain.recommendation.entity.RecommendedPlace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RecommendedPlaceRepository
        extends JpaRepository<RecommendedPlace, Long> {

    List<RecommendedPlace> findAllByRecommendedAreaId(Long recommendedAreaId);

    boolean existsByRecommendedAreaId(Long recommendedAreaId);

    // 특정 추천 실행(run)에 속한 추천 장소 id 전체 조회 (RecommendedArea 경유)
    @Query("""
            select rp.id
            from RecommendedPlace rp, RecommendedArea ra
            where rp.recommendedAreaId = ra.id
              and ra.recommendationRunId = :recommendationRunId
            """)
    List<Long> findIdsByRecommendationRunId(@Param("recommendationRunId") Long recommendationRunId);

}
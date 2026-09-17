package com.moive.MoiveBE.domain.recommendation.repository;

import com.moive.MoiveBE.domain.recommendation.entity.RecommendationRun;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecommendationRunRepository
        extends JpaRepository<RecommendationRun, Long> {

    Optional<RecommendationRun>
    findTopByMeetingIdAndStatusOrderByCreatedAtDesc(
            Long meetingId,
            RecommendationStatus status
    );

    List<RecommendationRun> findAllByMeetingIdInAndStatus(
            List<Long> meetingIds,
            RecommendationStatus status
    );
}
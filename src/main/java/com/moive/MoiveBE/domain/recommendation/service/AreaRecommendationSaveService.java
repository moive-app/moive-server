package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.dto.AreaCandidate;
import com.moive.MoiveBE.domain.recommendation.dto.AreaScoreResult;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendationRun;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendedArea;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendationRunRepository;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendedAreaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AreaRecommendationSaveService {

    private final RecommendationRunRepository recommendationRunRepository;
    private final RecommendedAreaRepository recommendedAreaRepository;

    @Transactional
    public RecommendationRun save(
            Long meetingId,
            List<AreaCandidate> candidates,
            List<AreaScoreResult> top3
    ) {

        RecommendationRun run =
                recommendationRunRepository.save(
                        RecommendationRun.create(meetingId)
                );

        List<RecommendedArea> recommendedAreas =
                top3.stream()
                        .map(scoreResult -> {

                            AreaCandidate candidate =
                                    candidates.get(
                                            scoreResult.destinationIndex()
                                    );

                            return RecommendedArea.create(
                                    run.getId(),
                                    candidate.name()
                            );
                        })
                        .toList();

        recommendedAreaRepository.saveAll(
                recommendedAreas
        );

        run.complete();

        return run;
    }
}
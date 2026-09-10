package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.dto.ParticipantRecommendationCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlacePreferenceAllocationService {

    private final CandidateAllocationService candidateAllocationService;

    public Map<String, Integer> allocate(
            List<ParticipantRecommendationCondition> participants
    ) {

        Map<String, Integer> selectionCounts = new LinkedHashMap<>();

        for (ParticipantRecommendationCondition participant : participants) {
            for (String preferenceType : participant.preferenceTypes()) {
                selectionCounts.merge(
                        preferenceType,
                        1,
                        Integer::sum
                );
            }
        }

        return candidateAllocationService.allocate(selectionCounts);
    }
}
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

        Map<String, Integer> orderedSelectionCounts =
                selectionCounts.entrySet().stream()
                        .sorted(
                                Map.Entry.<String, Integer>comparingByValue()
                                        .reversed()
                                        .thenComparing(Map.Entry.comparingByKey())
                        )
                        .collect(
                                LinkedHashMap::new,
                                (map, entry) ->
                                        map.put(
                                                entry.getKey(),
                                                entry.getValue()
                                        ),
                                LinkedHashMap::putAll
                        );

        return candidateAllocationService.allocate(
                orderedSelectionCounts
        );
    }
}
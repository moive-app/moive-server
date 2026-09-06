package com.moive.MoiveBE.domain.recommendation.dto;

import java.util.Set;

public record ParticipantRecommendationCondition(
        int originIndex,
        Set<String> preferenceTypes,
        Integer maxTravelMinutes
) {
}
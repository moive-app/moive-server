package com.moive.MoiveBE.domain.recommendation.dto;

import java.util.Set;

public record PlaceCandidate(
        String googlePlaceId,
        double latitude,
        double longitude,
        int candidateOrder,
        String preferenceType
) {
}
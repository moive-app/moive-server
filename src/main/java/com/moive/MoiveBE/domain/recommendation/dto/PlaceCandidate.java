package com.moive.MoiveBE.domain.recommendation.dto;

public record PlaceCandidate(
        String googlePlaceId,
        double latitude,
        double longitude,
        int candidateOrder
) {
}
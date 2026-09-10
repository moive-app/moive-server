package com.moive.MoiveBE.domain.recommendation.dto;

public record AreaCandidate(
        String name,
        String googlePlaceId,
        double latitude,
        double longitude
) {
}
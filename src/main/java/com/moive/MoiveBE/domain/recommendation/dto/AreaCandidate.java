package com.moive.MoiveBE.domain.recommendation.dto;

public record AreaCandidate(
        String name,
        String searchName,
        String signguCode,
        double latitude,
        double longitude
) {
}
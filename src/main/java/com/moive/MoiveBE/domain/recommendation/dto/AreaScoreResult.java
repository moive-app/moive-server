package com.moive.MoiveBE.domain.recommendation.dto;

public record AreaScoreResult(
        int destinationIndex,
        double normalizedAverage,
        double normalizedMax,
        double normalizedStandardDeviation,
        double score
) {
}
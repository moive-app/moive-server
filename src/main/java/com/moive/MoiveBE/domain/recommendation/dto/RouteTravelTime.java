package com.moive.MoiveBE.domain.recommendation.dto;

public record RouteTravelTime(
        int originIndex,
        int destinationIndex,
        double durationSeconds
) {
}
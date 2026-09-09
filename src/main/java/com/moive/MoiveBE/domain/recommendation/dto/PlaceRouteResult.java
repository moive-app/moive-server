package com.moive.MoiveBE.domain.recommendation.dto;

public record PlaceRouteResult(
        int destinationIndex,
        double averageTravelSeconds,
        double maxTravelSeconds
) {
}
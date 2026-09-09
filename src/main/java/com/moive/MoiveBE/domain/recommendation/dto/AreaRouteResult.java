package com.moive.MoiveBE.domain.recommendation.dto;

public record AreaRouteResult(
        int destinationIndex,
        double averageTravelSeconds,
        double maxTravelSeconds,
        double standardDeviationSeconds
) {
}
package com.moive.MoiveBE.domain.recommendation.dto;

public record PlaceRankingResult(
        String googlePlaceId,
        int destinationIndex,
        int candidateOrder,
        int preferenceMatchCnt,
        double averageTravelSeconds,
        double maxTravelSeconds
) {
}
package com.moive.MoiveBE.domain.recommendation.dto;

public record PlaceMatchResult(
        int destinationIndex,
        int preferenceMatchCnt
) {
}
package com.moive.MoiveBE.domain.recommendation.dto;

import java.util.List;

public record RecommendedPlaceListResponse(
        Long recommendedAreaId,
        List<Place> places
) {

    public record Place(
            Long recommendedPlaceId,
            String name,
            String category,
            Integer preferenceMatchRate,
            Integer averageTravelTime,
            Integer maxTravelTime,
            Integer preferenceMatchCnt
    ) {
    }
}
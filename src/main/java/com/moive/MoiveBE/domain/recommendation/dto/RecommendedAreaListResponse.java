package com.moive.MoiveBE.domain.recommendation.dto;

import java.util.List;

public record RecommendedAreaListResponse(
        List<Area> areas
) {

    public record Area(
            Long recommendedAreaId,
            String name,
            double latitude,
            double longitude
    ) {
    }
}
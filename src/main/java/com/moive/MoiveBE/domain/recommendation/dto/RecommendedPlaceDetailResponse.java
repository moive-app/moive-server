package com.moive.MoiveBE.domain.recommendation.dto;

import java.util.List;

public record RecommendedPlaceDetailResponse(
        Long recommendedPlaceId,
        String name,
        String category,
        String address,
        String areaName,
        Integer participantCnt,
        Integer preferenceMatchCnt,
        Integer averageTravelTime,
        List<String> imageUrls
) {
}
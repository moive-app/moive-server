package com.moive.MoiveBE.domain.recommendation.dto;

import java.util.List;

public record GooglePlaceDetailsResponse(
        LocalizedText displayName,
        LocalizedText primaryTypeDisplayName,
        String formattedAddress,
        List<Photo> photos,
        Location location
) {
    public record LocalizedText(
            String text,
            String languageCode
    ) {
    }

    public record Photo(
            String name
    ) {}

    public record Location(
            Double latitude,
            Double longitude
    ) {}
}
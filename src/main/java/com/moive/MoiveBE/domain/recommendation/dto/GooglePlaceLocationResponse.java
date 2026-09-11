package com.moive.MoiveBE.domain.recommendation.dto;

public record GooglePlaceLocationResponse(
        LocalizedText displayName,
        LocalizedText primaryTypeDisplayName,
        String formattedAddress,
        Location location
) {
    public record LocalizedText(
            String text,
            String languageCode
    ) {
    }

    public record Location(
            double latitude,
            double longitude
    ) {
    }
}

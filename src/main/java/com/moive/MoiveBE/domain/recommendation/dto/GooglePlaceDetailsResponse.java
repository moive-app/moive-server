package com.moive.MoiveBE.domain.recommendation.dto;

public record GooglePlaceDetailsResponse(
        LocalizedText displayName,
        LocalizedText primaryTypeDisplayName
) {
    public record LocalizedText(
            String text,
            String languageCode
    ) {
    }
}
package com.moive.MoiveBE.domain.recommendation.dto;

import java.util.List;

public record GooglePlaceSearchResponse(
        List<Place> places
) {

    public record Place(
            String id,
            Location location
    ) {
    }

    public record Location(
            double latitude,
            double longitude
    ) {
    }
}
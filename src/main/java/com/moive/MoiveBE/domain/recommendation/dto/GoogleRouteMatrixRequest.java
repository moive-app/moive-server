package com.moive.MoiveBE.domain.recommendation.dto;

import java.util.List;

public record GoogleRouteMatrixRequest(
        List<RouteMatrixOrigin> origins,
        List<RouteMatrixDestination> destinations,
        String travelMode
) {

    public record RouteMatrixOrigin(
            Waypoint waypoint
    ) {
    }

    public record RouteMatrixDestination(
            Waypoint waypoint
    ) {
    }

    public record Waypoint(
            Location location
    ) {
    }

    public record Location(
            LatLng latLng
    ) {
    }

    public record LatLng(
            double latitude,
            double longitude
    ) {
    }
}
package com.moive.MoiveBE.domain.route.dto;

import java.util.List;

public record KakaoTransitRouteResponse(
        String status,
        Properties properties,
        List<Route> routes
) {

    public record Properties(
            Integer total,
            Integer bus,
            Integer subway,
            Integer busAndSubway,
            String landingURL
    ) {
    }

    public record Route(
            RouteProperties properties,
            List<Step> steps
    ) {
    }

    public record RouteProperties(
            String type,
            Integer totalDistance,
            Integer totalTime,
            Integer transfers,
            Fare fare
    ) {
    }

    public record Fare(
            Integer value,
            Integer min,
            Integer max
    ) {
    }

    public record Step(
            StepProperties properties,
            Path path
    ) {
    }

    public record StepProperties(
            String guidance,
            String type,
            Integer distance,
            Integer time,
            List<Stop> stops,
            List<Vehicle> vehicles
    ) {
    }

    public record Stop(String name) {
    }

    public record Vehicle(String type, String name) {
    }

    public record Path(Double[][] points) {
    }
}

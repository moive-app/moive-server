package com.moive.MoiveBE.domain.recommendation.dto;

public record GoogleRouteMatrixResponse(
        int originIndex,
        int destinationIndex,
        Status status,
        String condition,
        String duration
) {
    public record Status(
            Integer code,
            String message
    ) {
    }
}
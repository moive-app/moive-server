package com.moive.MoiveBE.domain.recommendation.service;

import org.springframework.stereotype.Service;

@Service
public class AreaDistanceService {

    private static final double EARTH_RADIUS_KM = 6371.0;

    public double calculateDistanceKm(
            double latitude1,
            double longitude1,
            double latitude2,
            double longitude2
    ) {

        double latitudeDistance =
                Math.toRadians(latitude2 - latitude1);

        double longitudeDistance =
                Math.toRadians(longitude2 - longitude1);

        double a =
                Math.sin(latitudeDistance / 2)
                        * Math.sin(latitudeDistance / 2)
                        + Math.cos(Math.toRadians(latitude1))
                        * Math.cos(Math.toRadians(latitude2))
                        * Math.sin(longitudeDistance / 2)
                        * Math.sin(longitudeDistance / 2);

        double c =
                2 * Math.atan2(
                        Math.sqrt(a),
                        Math.sqrt(1 - a)
                );

        return EARTH_RADIUS_KM * c;
    }

    public boolean isWithin5Km(
            double centerLatitude,
            double centerLongitude,
            double candidateLatitude,
            double candidateLongitude
    ) {
        return calculateDistanceKm(
                centerLatitude,
                centerLongitude,
                candidateLatitude,
                candidateLongitude
        ) <= 5.0;
    }
}
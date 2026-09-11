package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.client.GooglePlacesClient;
import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AreaCandidateResolver {

    private final GooglePlacesClient googlePlacesClient;
    private final AreaDistanceService areaDistanceService;

    public GooglePlaceSearchResponse.Place resolve(
            String areaName,
            double centerLatitude,
            double centerLongitude
    ) {

        // 1순위: 지하철역
        GooglePlaceSearchResponse subwayResponse =
                googlePlacesClient.searchAreaPlace(
                        areaName + " 지하철역",
                        centerLatitude,
                        centerLongitude
                );

        GooglePlaceSearchResponse.Place subwayPlace =
                getValidPlace(
                        subwayResponse,
                        centerLatitude,
                        centerLongitude
                );

        if (subwayPlace != null) {
            return subwayPlace;
        }

        // 2순위: 버스 정류장
        GooglePlaceSearchResponse transitResponse =
                googlePlacesClient.searchAreaPlace(
                        areaName + " 버스 정류장",
                        centerLatitude,
                        centerLongitude
                );

        GooglePlaceSearchResponse.Place transitPlace =
                getValidPlace(
                        transitResponse,
                        centerLatitude,
                        centerLongitude
                );

        if (transitPlace != null) {
            return transitPlace;
        }

        // 3순위: 지역 자체
        GooglePlaceSearchResponse areaResponse =
                googlePlacesClient.searchAreaPlace(
                        areaName,
                        centerLatitude,
                        centerLongitude
                );

        return getValidPlace(
                areaResponse,
                centerLatitude,
                centerLongitude
        );
    }

    private GooglePlaceSearchResponse.Place getValidPlace(
            GooglePlaceSearchResponse response,
            double centerLatitude,
            double centerLongitude
    ) {

        if (response == null
                || response.places() == null
                || response.places().isEmpty()) {
            return null;
        }

        GooglePlaceSearchResponse.Place place =
                response.places().get(0);

        boolean within5Km =
                areaDistanceService.isWithin5Km(
                        centerLatitude,
                        centerLongitude,
                        place.location().latitude(),
                        place.location().longitude()
                );

        if (!within5Km) {
            return null;
        }

        return place;
    }
}
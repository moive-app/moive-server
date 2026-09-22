package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.client.GooglePlacesClient;
import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AreaCandidateResolver {

    private final GooglePlacesClient googlePlacesClient;

    public GooglePlaceSearchResponse.Place resolve(
            String areaName,
            double centerLatitude,
            double centerLongitude,
            Set<String> usedPlaceIds,
            Set<String> usedCoordinates
    ) {

        log.info(
                "지역 좌표 조회 시작 - areaName={}, center=({}, {})",
                areaName,
                centerLatitude,
                centerLongitude
        );

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
                        usedPlaceIds,
                        usedCoordinates
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
                        usedPlaceIds,
                        usedCoordinates
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
                usedPlaceIds,
                usedCoordinates
        );
    }

    private GooglePlaceSearchResponse.Place getValidPlace(
            GooglePlaceSearchResponse response,
            Set<String> usedPlaceIds,
            Set<String> usedCoordinates
    ) {

        if (response == null
                || response.places() == null
                || response.places().isEmpty()) {

            log.warn(
                    "지역 좌표 조회 실패 - Google Places 검색 결과 없음"
            );

            return null;
        }

        GooglePlaceSearchResponse.Place place =
                response.places().get(0);

        if (place.location() == null) {
            log.warn(
                    "지역 좌표 조회 실패 - Google Place location 없음"
            );
            return null;
        }

        if (usedPlaceIds.contains(place.id())) {
            log.info(
                    "지역 좌표 중복 - 이미 사용된 Google Place - placeId={}",
                    place.id()
            );
            return null;
        }

        String coordinateKey =
                createCoordinateKey(
                        place.location().latitude(),
                        place.location().longitude()
                );

        if (usedCoordinates.contains(coordinateKey)) {
            log.info(
                    "지역 좌표 중복 - 이미 사용된 좌표 - latitude={}, longitude={}",
                    place.location().latitude(),
                    place.location().longitude()
            );
            return null;
        }

        log.info(
                "지역 좌표 조회 성공 - placeId={}, place=({}, {})",
                place.id(),
                place.location().latitude(),
                place.location().longitude()
        );

        return place;
    }

    public String createCoordinateKey(
            double latitude,
            double longitude
    ) {
        return latitude + "," + longitude;
    }
}
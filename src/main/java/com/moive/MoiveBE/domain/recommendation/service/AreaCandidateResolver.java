package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.client.GooglePlacesClient;
import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AreaCandidateResolver {

    private final GooglePlacesClient googlePlacesClient;

    public GooglePlaceSearchResponse.Place resolve(
            String areaName,
            double centerLatitude,
            double centerLongitude
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
                getValidPlace(subwayResponse);

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
                getValidPlace(transitResponse);

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

        return getValidPlace(areaResponse);
    }

    private GooglePlaceSearchResponse.Place getValidPlace(
            GooglePlaceSearchResponse response
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

        log.info(
                "지역 좌표 조회 성공 - place=({}, {})",
                place.location().latitude(),
                place.location().longitude()
        );

        return place;
    }
}
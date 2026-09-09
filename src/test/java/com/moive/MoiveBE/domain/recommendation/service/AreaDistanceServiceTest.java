package com.moive.MoiveBE.domain.recommendation.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AreaDistanceServiceTest {

    private final AreaDistanceService areaDistanceService =
            new AreaDistanceService();

    @Test
    void 후보_지역이_중심_좌표_5km_이내인지_확인한다() {

        double centerLatitude = 37.4979;
        double centerLongitude = 127.0276;

        // Google Places API에서 조회한 역삼역 좌표
        double candidateLatitude = 37.500643;
        double candidateLongitude = 127.036377;

        double distance =
                areaDistanceService.calculateDistanceKm(
                        centerLatitude,
                        centerLongitude,
                        candidateLatitude,
                        candidateLongitude
                );

        System.out.println("distance = " + distance + " km");

        assertThat(
                areaDistanceService.isWithin5Km(
                        centerLatitude,
                        centerLongitude,
                        candidateLatitude,
                        candidateLongitude
                )
        ).isTrue();
    }
}
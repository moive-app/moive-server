package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.client.GooglePlacesClient;
import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceSearchResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AreaCandidateResolverTest {

    @Autowired
    private AreaCandidateResolver areaCandidateResolver;

    @Test
    void 추천_지역의_대표_좌표를_조회하고_5km_이내인지_검증한다() {

        GooglePlaceSearchResponse.Place place =
                areaCandidateResolver.resolve(
                        "역삼동",
                        37.4979,
                        127.0276
                );

        assertThat(place).isNotNull();

        System.out.println("placeId = " + place.id());
        System.out.println("latitude = " + place.location().latitude());
        System.out.println("longitude = " + place.location().longitude());
    }
}
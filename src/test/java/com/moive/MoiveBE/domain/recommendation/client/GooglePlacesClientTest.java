package com.moive.MoiveBE.domain.recommendation.client;

import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceSearchResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GooglePlacesClientTest {

    @Autowired
    private GooglePlacesClient googlePlacesClient;

    @Test
    void 구글_장소_검색_테스트() {

        GooglePlaceSearchResponse response =
                googlePlacesClient.searchPlaces("강남역 한식", 3);

        assertThat(response).isNotNull();
        assertThat(response.places()).isNotNull();
        assertThat(response.places()).isNotEmpty();

        response.places().forEach(place -> {
            System.out.println("placeId = " + place.id());
            System.out.println("latitude = " + place.location().latitude());
            System.out.println("longitude = " + place.location().longitude());
            System.out.println("--------------------");
        });
    }
}
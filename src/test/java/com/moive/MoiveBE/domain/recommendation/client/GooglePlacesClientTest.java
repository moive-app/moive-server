package com.moive.MoiveBE.domain.recommendation.client;

import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceDetailsResponse;
import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceSearchResponse;
import org.junit.jupiter.api.Disabled;
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

    @Test
    void Google_Place_Details를_조회한다() {

        String googlePlaceId = "ChIJ32V8iv6hfDUR-UMjO61PeWE";

        GooglePlaceDetailsResponse response =
                googlePlacesClient.getPlaceDetails(googlePlaceId);

        System.out.println(response);

        assertThat(response).isNotNull();
        assertThat(response.displayName()).isNotNull();
        assertThat(response.displayName().text()).isNotBlank();
        assertThat(response.primaryTypeDisplayName()).isNotNull();
        assertThat(response.primaryTypeDisplayName().text()).isNotBlank();
        assertThat(response.formattedAddress()).isNotBlank();

        assertThat(response.photos()).isNotNull();
        assertThat(response.photos()).isNotEmpty();
    }

    @Test
    void Google_Place_Photo_URL을_조회한다() {

        String googlePlaceId = "ChIJ32V8iv6hfDUR-UMjO61PeWE";

        GooglePlaceDetailsResponse details =
                googlePlacesClient.getPlaceDetails(googlePlaceId);

        assertThat(details).isNotNull();
        assertThat(details.photos()).isNotNull();
        assertThat(details.photos()).isNotEmpty();

        String photoName = details.photos().get(0).name();

        String photoUrl =
                googlePlacesClient.getPlacePhotoUrl(photoName);

        System.out.println("photoUrl = " + photoUrl);

        assertThat(photoUrl).isNotBlank();
    }

    @Test
    void 추천_지역_대표_좌표를_조회한다() {

        GooglePlaceSearchResponse response =
                googlePlacesClient.searchAreaPlace(
                        "역삼역",
                        37.4979,
                        127.0276
                );

        System.out.println(response);

        assertThat(response).isNotNull();
        assertThat(response.places()).isNotEmpty();

        GooglePlaceSearchResponse.Place place =
                response.places().get(0);

        System.out.println("placeId = " + place.id());
        System.out.println("latitude = " + place.location().latitude());
        System.out.println("longitude = " + place.location().longitude());
    }
}
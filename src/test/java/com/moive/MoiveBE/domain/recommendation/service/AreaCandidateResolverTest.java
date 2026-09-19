package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.client.GooglePlacesClient;
import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceSearchResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AreaCandidateResolverTest {

    @Mock
    private GooglePlacesClient googlePlacesClient;

    @InjectMocks
    private AreaCandidateResolver areaCandidateResolver;

    @Test
    void 추천_지역의_대표_좌표를_조회한다() {

        double centerLatitude = 37.4979;
        double centerLongitude = 127.0276;

        GooglePlaceSearchResponse.Place place =
                new GooglePlaceSearchResponse.Place(
                        "test-place-id",
                        new GooglePlaceSearchResponse.Location(
                                37.5006,
                                127.0363
                        )
                );

        GooglePlaceSearchResponse response =
                new GooglePlaceSearchResponse(
                        List.of(place)
                );

        when(
                googlePlacesClient.searchAreaPlace(
                        "서울특별시 강남구 역삼동 지하철역",
                        centerLatitude,
                        centerLongitude
                )
        ).thenReturn(response);

        GooglePlaceSearchResponse.Place result =
                areaCandidateResolver.resolve(
                        "서울특별시 강남구 역삼동",
                        centerLatitude,
                        centerLongitude
                );

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo("test-place-id");
        assertThat(result.location().latitude()).isEqualTo(37.5006);
        assertThat(result.location().longitude()).isEqualTo(127.0363);
    }
}
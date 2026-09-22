package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.client.GooglePlacesClient;
import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceSearchResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

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
                        centerLongitude,
                        Set.of(),
                        Set.of()
                );

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo("test-place-id");
        assertThat(result.location().latitude()).isEqualTo(37.5006);
        assertThat(result.location().longitude()).isEqualTo(127.0363);
    }

    @Test
    void 이미_사용된_지하철역이면_버스정류장을_조회한다() {

        String areaName =
                "서울특별시 강남구 역삼동";

        double centerLatitude = 37.4979;
        double centerLongitude = 127.0276;

        GooglePlaceSearchResponse.Place subwayPlace =
                new GooglePlaceSearchResponse.Place(
                        "duplicate-place-id",
                        new GooglePlaceSearchResponse.Location(
                                37.5000,
                                127.0300
                        )
                );

        GooglePlaceSearchResponse.Place transitPlace =
                new GooglePlaceSearchResponse.Place(
                        "transit-place-id",
                        new GooglePlaceSearchResponse.Location(
                                37.5010,
                                127.0310
                        )
                );

        when(
                googlePlacesClient.searchAreaPlace(
                        areaName + " 지하철역",
                        centerLatitude,
                        centerLongitude
                )
        ).thenReturn(
                new GooglePlaceSearchResponse(
                        List.of(subwayPlace)
                )
        );

        when(
                googlePlacesClient.searchAreaPlace(
                        areaName + " 버스 정류장",
                        centerLatitude,
                        centerLongitude
                )
        ).thenReturn(
                new GooglePlaceSearchResponse(
                        List.of(transitPlace)
                )
        );

        Set<String> usedPlaceIds =
                Set.of("duplicate-place-id");

        GooglePlaceSearchResponse.Place result =
                areaCandidateResolver.resolve(
                        areaName,
                        centerLatitude,
                        centerLongitude,
                        usedPlaceIds,
                        Set.of()
                );

        assertThat(result).isNotNull();
        assertThat(result.id())
                .isEqualTo("transit-place-id");

        verify(googlePlacesClient)
                .searchAreaPlace(
                        areaName + " 지하철역",
                        centerLatitude,
                        centerLongitude
                );

        verify(googlePlacesClient)
                .searchAreaPlace(
                        areaName + " 버스 정류장",
                        centerLatitude,
                        centerLongitude
                );

        verify(
                googlePlacesClient,
                never()
        ).searchAreaPlace(
                areaName,
                centerLatitude,
                centerLongitude
        );
    }

    @Test
    void 지하철역과_버스정류장이_모두_사용된_장소이면_지역_자체를_조회한다() {

        String areaName =
                "서울특별시 강남구 역삼동";

        double centerLatitude = 37.4979;
        double centerLongitude = 127.0276;

        GooglePlaceSearchResponse.Place subwayPlace =
                new GooglePlaceSearchResponse.Place(
                        "subway-place-id",
                        new GooglePlaceSearchResponse.Location(
                                37.5000,
                                127.0300
                        )
                );

        GooglePlaceSearchResponse.Place transitPlace =
                new GooglePlaceSearchResponse.Place(
                        "transit-place-id",
                        new GooglePlaceSearchResponse.Location(
                                37.5010,
                                127.0310
                        )
                );

        GooglePlaceSearchResponse.Place areaPlace =
                new GooglePlaceSearchResponse.Place(
                        "area-place-id",
                        new GooglePlaceSearchResponse.Location(
                                37.5020,
                                127.0320
                        )
                );

        when(
                googlePlacesClient.searchAreaPlace(
                        areaName + " 지하철역",
                        centerLatitude,
                        centerLongitude
                )
        ).thenReturn(
                new GooglePlaceSearchResponse(
                        List.of(subwayPlace)
                )
        );

        when(
                googlePlacesClient.searchAreaPlace(
                        areaName + " 버스 정류장",
                        centerLatitude,
                        centerLongitude
                )
        ).thenReturn(
                new GooglePlaceSearchResponse(
                        List.of(transitPlace)
                )
        );

        when(
                googlePlacesClient.searchAreaPlace(
                        areaName,
                        centerLatitude,
                        centerLongitude
                )
        ).thenReturn(
                new GooglePlaceSearchResponse(
                        List.of(areaPlace)
                )
        );

        Set<String> usedPlaceIds =
                Set.of(
                        "subway-place-id",
                        "transit-place-id"
                );

        GooglePlaceSearchResponse.Place result =
                areaCandidateResolver.resolve(
                        areaName,
                        centerLatitude,
                        centerLongitude,
                        usedPlaceIds,
                        Set.of()
                );

        assertThat(result).isNotNull();
        assertThat(result.id())
                .isEqualTo("area-place-id");

        verify(googlePlacesClient)
                .searchAreaPlace(
                        areaName + " 지하철역",
                        centerLatitude,
                        centerLongitude
                );

        verify(googlePlacesClient)
                .searchAreaPlace(
                        areaName + " 버스 정류장",
                        centerLatitude,
                        centerLongitude
                );

        verify(googlePlacesClient)
                .searchAreaPlace(
                        areaName,
                        centerLatitude,
                        centerLongitude
                );
    }

    @Test
    void 다른_장소라도_이미_사용된_좌표이면_다음_후보를_조회한다() {

        String areaName =
                "서울특별시 강남구 역삼동";

        double centerLatitude = 37.4979;
        double centerLongitude = 127.0276;

        GooglePlaceSearchResponse.Place subwayPlace =
                new GooglePlaceSearchResponse.Place(
                        "new-subway-place-id",
                        new GooglePlaceSearchResponse.Location(
                                37.5000,
                                127.0300
                        )
                );

        GooglePlaceSearchResponse.Place transitPlace =
                new GooglePlaceSearchResponse.Place(
                        "transit-place-id",
                        new GooglePlaceSearchResponse.Location(
                                37.5010,
                                127.0310
                        )
                );

        when(
                googlePlacesClient.searchAreaPlace(
                        areaName + " 지하철역",
                        centerLatitude,
                        centerLongitude
                )
        ).thenReturn(
                new GooglePlaceSearchResponse(
                        List.of(subwayPlace)
                )
        );

        when(
                googlePlacesClient.searchAreaPlace(
                        areaName + " 버스 정류장",
                        centerLatitude,
                        centerLongitude
                )
        ).thenReturn(
                new GooglePlaceSearchResponse(
                        List.of(transitPlace)
                )
        );

        Set<String> usedCoordinates =
                Set.of("37.5,127.03");

        GooglePlaceSearchResponse.Place result =
                areaCandidateResolver.resolve(
                        areaName,
                        centerLatitude,
                        centerLongitude,
                        Set.of(),
                        usedCoordinates
                );

        assertThat(result).isNotNull();

        assertThat(result.id())
                .isEqualTo("transit-place-id");

        assertThat(result.location().latitude())
                .isEqualTo(37.5010);

        assertThat(result.location().longitude())
                .isEqualTo(127.0310);

        verify(googlePlacesClient)
                .searchAreaPlace(
                        areaName + " 지하철역",
                        centerLatitude,
                        centerLongitude
                );

        verify(googlePlacesClient)
                .searchAreaPlace(
                        areaName + " 버스 정류장",
                        centerLatitude,
                        centerLongitude
                );

        verify(
                googlePlacesClient,
                never()
        ).searchAreaPlace(
                areaName,
                centerLatitude,
                centerLongitude
        );
    }
}
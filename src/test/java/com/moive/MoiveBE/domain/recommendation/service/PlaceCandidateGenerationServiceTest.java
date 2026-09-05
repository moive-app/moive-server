package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.client.GooglePlacesClient;
import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceSearchResponse;
import com.moive.MoiveBE.domain.recommendation.dto.PlaceCandidate;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PlaceCandidateGenerationServiceTest {

    private final GooglePlacesClient googlePlacesClient =
            mock(GooglePlacesClient.class);

    private final PlaceCandidateService placeCandidateService =
            new PlaceCandidateService();

    private final PlaceCandidateGenerationService service =
            new PlaceCandidateGenerationService(
                    googlePlacesClient,
                    placeCandidateService
            );

    @Test
    void 지역과_취향별_할당량으로_장소_후보를_생성한다() {

        Map<String, Integer> allocations = new LinkedHashMap<>();
        allocations.put("한식", 2);
        allocations.put("카페", 2);

        when(googlePlacesClient.searchPlaces("강남역 한식", 2))
                .thenReturn(response(
                        place("A", 37.1, 127.1),
                        place("B", 37.2, 127.2)
                ));

        when(googlePlacesClient.searchPlaces("강남역 카페", 2))
                .thenReturn(response(
                        place("B", 37.2, 127.2),
                        place("C", 37.3, 127.3)
                ));

        List<PlaceCandidate> result =
                service.generateCandidates("강남역", allocations);

        assertThat(result).hasSize(3);

        assertThat(result.get(0).googlePlaceId()).isEqualTo("A");
        assertThat(result.get(0).candidateOrder()).isEqualTo(0);

        assertThat(result.get(1).googlePlaceId()).isEqualTo("B");
        assertThat(result.get(1).candidateOrder()).isEqualTo(1);

        assertThat(result.get(2).googlePlaceId()).isEqualTo("C");
        assertThat(result.get(2).candidateOrder()).isEqualTo(2);

        verify(googlePlacesClient)
                .searchPlaces("강남역 한식", 2);

        verify(googlePlacesClient)
                .searchPlaces("강남역 카페", 2);
    }

    private GooglePlaceSearchResponse response(
            GooglePlaceSearchResponse.Place... places
    ) {
        return new GooglePlaceSearchResponse(List.of(places));
    }

    private GooglePlaceSearchResponse.Place place(
            String id,
            double latitude,
            double longitude
    ) {
        return new GooglePlaceSearchResponse.Place(
                id,
                new GooglePlaceSearchResponse.Location(
                        latitude,
                        longitude
                )
        );
    }
}
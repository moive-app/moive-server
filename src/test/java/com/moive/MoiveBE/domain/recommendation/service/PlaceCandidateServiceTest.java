package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceSearchResponse;
import com.moive.MoiveBE.domain.recommendation.dto.PlaceCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceCandidateServiceTest {

    private final PlaceCandidateService placeCandidateService =
            new PlaceCandidateService();

    @Test
    void 구글_장소_검색_결과를_후보로_변환하고_중복을_제거한다() {

        List<GooglePlaceSearchResponse.Place> places = List.of(
                createPlace("A", 37.1, 127.1),
                createPlace("B", 37.2, 127.2),
                createPlace("B", 37.2, 127.2),
                createPlace("C", 37.3, 127.3)
        );

        List<PlaceCandidate> result =
                placeCandidateService.createCandidates(places);

        assertThat(result).hasSize(3);

        assertThat(result.get(0).googlePlaceId()).isEqualTo("A");
        assertThat(result.get(0).candidateOrder()).isEqualTo(0);

        assertThat(result.get(1).googlePlaceId()).isEqualTo("B");
        assertThat(result.get(1).candidateOrder()).isEqualTo(1);

        assertThat(result.get(2).googlePlaceId()).isEqualTo("C");
        assertThat(result.get(2).candidateOrder()).isEqualTo(2);
    }

    private GooglePlaceSearchResponse.Place createPlace(
            String placeId,
            double latitude,
            double longitude
    ) {
        return new GooglePlaceSearchResponse.Place(
                placeId,
                new GooglePlaceSearchResponse.Location(
                        latitude,
                        longitude
                )
        );
    }
}
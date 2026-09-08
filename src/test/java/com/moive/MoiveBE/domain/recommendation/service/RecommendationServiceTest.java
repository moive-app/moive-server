package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.client.GooglePlacesClient;
import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceDetailsResponse;
import com.moive.MoiveBE.domain.recommendation.dto.RecommendedPlaceListResponse;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendedPlace;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendedPlaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private RecommendedPlaceRepository recommendedPlaceRepository;

    @Mock
    private GooglePlacesClient googlePlacesClient;

    @InjectMocks
    private RecommendationService recommendationService;

    @Test
    void 추천_장소_목록을_조회한다() {

        Long recommendedAreaId = 1L;

        RecommendedPlace recommendedPlace =
                RecommendedPlace.create(
                        recommendedAreaId,
                        "google-place-id",
                        3
                );

        given(recommendedPlaceRepository
                .findAllByRecommendedAreaId(recommendedAreaId))
                .willReturn(List.of(recommendedPlace));

        GooglePlaceDetailsResponse details =
                new GooglePlaceDetailsResponse(
                        new GooglePlaceDetailsResponse.LocalizedText(
                                "다몽집 신논현본점",
                                "ko"
                        ),
                        new GooglePlaceDetailsResponse.LocalizedText(
                                "한식 음식점",
                                "ko"
                        )
                );

        given(googlePlacesClient.getPlaceDetails("google-place-id"))
                .willReturn(details);

        RecommendedPlaceListResponse response =
                recommendationService.getRecommendedPlaces(recommendedAreaId);

        assertThat(response.recommendedAreaId())
                .isEqualTo(recommendedAreaId);

        assertThat(response.places()).hasSize(1);

        RecommendedPlaceListResponse.Place place =
                response.places().get(0);

        assertThat(place.name()).isEqualTo("다몽집 신논현본점");
        assertThat(place.category()).isEqualTo("한식 음식점");
        assertThat(place.preferenceMatchCnt()).isEqualTo(3);
    }
}
package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.client.GooglePlacesClient;
import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceDetailsResponse;
import com.moive.MoiveBE.domain.recommendation.dto.RecommendedPlaceDetailResponse;
import com.moive.MoiveBE.domain.recommendation.dto.RecommendedPlaceListResponse;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendationRun;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendedArea;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendedPlace;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendationRunRepository;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendedAreaRepository;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendedPlaceRepository;
import com.moive.MoiveBE.global.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private RecommendedPlaceRepository recommendedPlaceRepository;

    @Mock
    private GooglePlacesClient googlePlacesClient;

    @Mock
    private RecommendedAreaRepository recommendedAreaRepository;

    @Mock
    private RecommendationRunRepository recommendationRunRepository;

    @Mock
    private PlaceRecommendationGenerationService placeRecommendationGenerationService;

    @InjectMocks
    private RecommendationService recommendationService;

    @Test
    void 추천_장소_목록을_조회한다() {

        Long meetingId = 1L;
        Long recommendedAreaId = 1L;
        Long recommendationRunId = 10L;

        RecommendedArea recommendedArea = mock(RecommendedArea.class);
        RecommendationRun recommendationRun = mock(RecommendationRun.class);

        given(recommendedAreaRepository.findById(recommendedAreaId))
                .willReturn(Optional.of(recommendedArea));

        given(recommendedArea.getRecommendationRunId())
                .willReturn(recommendationRunId);

        given(recommendationRunRepository.findById(recommendationRunId))
                .willReturn(Optional.of(recommendationRun));

        given(recommendationRun.getMeetingId())
                .willReturn(meetingId);

        given(recommendedPlaceRepository
                .existsByRecommendedAreaId(recommendedAreaId))
                .willReturn(true);

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
                        new GooglePlaceDetailsResponse.LocalizedText("다몽집", "ko"),
                        new GooglePlaceDetailsResponse.LocalizedText("한식", "ko"),
                        "서울특별시 강남구 테헤란로 123",
                        List.of(
                                new GooglePlaceDetailsResponse.Photo(
                                        "places/test/photos/1"
                                )
                        )
                );

        given(googlePlacesClient.getPlaceSummaryDetails("google-place-id"))
                .willReturn(details);

        RecommendedPlaceListResponse response =
                recommendationService.getRecommendedPlaces(
                        meetingId,
                        recommendedAreaId
                );

        assertThat(response.recommendedAreaId())
                .isEqualTo(recommendedAreaId);

        assertThat(response.places()).hasSize(1);

        RecommendedPlaceListResponse.Place place =
                response.places().get(0);

        assertThat(place.name()).isEqualTo("다몽집");
        assertThat(place.category()).isEqualTo("한식");
        assertThat(place.preferenceMatchCnt()).isEqualTo(3);
    }

    @Test
    void 추천_장소_상세를_조회한다() {

        Long recommendedAreaId = 1L;
        Long recommendedPlaceId = 1L;

        RecommendedPlace recommendedPlace =
                RecommendedPlace.create(
                        recommendedAreaId,
                        "google-place-id",
                        3
                );

        given(recommendedPlaceRepository.findById(recommendedPlaceId))
                .willReturn(Optional.of(recommendedPlace));

        GooglePlaceDetailsResponse details =
                new GooglePlaceDetailsResponse(
                        new GooglePlaceDetailsResponse.LocalizedText(
                                "다몽집 | damongzip",
                                "ko"
                        ),
                        new GooglePlaceDetailsResponse.LocalizedText(
                                "한식 고기구이 레스토랑",
                                "ko"
                        ),
                        "서울특별시 강남구 테헤란로 123",
                        List.of(
                                new GooglePlaceDetailsResponse.Photo("places/test/photos/1"),
                                new GooglePlaceDetailsResponse.Photo("places/test/photos/2"),
                                new GooglePlaceDetailsResponse.Photo("places/test/photos/3")
                        )
                );

        given(googlePlacesClient.getPlaceDetails("google-place-id"))
                .willReturn(details);

        given(googlePlacesClient.getPlacePhotoUrl("places/test/photos/1"))
                .willReturn("https://example.com/photo1.jpg");

        given(googlePlacesClient.getPlacePhotoUrl("places/test/photos/2"))
                .willReturn("https://example.com/photo2.jpg");

        given(googlePlacesClient.getPlacePhotoUrl("places/test/photos/3"))
                .willReturn("https://example.com/photo3.jpg");

        RecommendedPlaceDetailResponse response =
                recommendationService.getRecommendedPlaceDetail(
                        recommendedAreaId,
                        recommendedPlaceId
                );

        assertThat(response.name()).isEqualTo("다몽집");
        assertThat(response.category()).isEqualTo("한식 고기구이 레스토랑");
        assertThat(response.address()).isEqualTo("서울특별시 강남구 테헤란로 123");
        assertThat(response.preferenceMatchCnt()).isEqualTo(3);
        assertThat(response.averageTravelTime()).isNull();
        assertThat(response.imageUrls()).containsExactly(
                "https://example.com/photo1.jpg",
                "https://example.com/photo2.jpg",
                "https://example.com/photo3.jpg"
        );
    }

    @Test
    void 존재하지_않는_추천_장소를_조회하면_예외가_발생한다() {

        Long recommendedAreaId = 1L;
        Long recommendedPlaceId = 999L;

        given(recommendedPlaceRepository.findById(recommendedPlaceId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                recommendationService.getRecommendedPlaceDetail(
                        recommendedAreaId,
                        recommendedPlaceId
                )
        )
                .isInstanceOf(CustomException.class);
    }

    @Test
    void 다른_추천_지역의_장소를_조회하면_예외가_발생한다() {

        Long requestedAreaId = 1L;
        Long recommendedPlaceId = 1L;

        RecommendedPlace recommendedPlace =
                RecommendedPlace.create(
                        2L,
                        "google-place-id",
                        3
                );

        given(recommendedPlaceRepository.findById(recommendedPlaceId))
                .willReturn(Optional.of(recommendedPlace));

        assertThatThrownBy(() ->
                recommendationService.getRecommendedPlaceDetail(
                        requestedAreaId,
                        recommendedPlaceId
                )
        )
                .isInstanceOf(CustomException.class);
    }

    @Test
    void 추천_장소가_없으면_생성한_후_조회한다() {

        Long meetingId = 1L;
        Long recommendedAreaId = 1L;
        Long recommendationRunId = 10L;

        RecommendedArea recommendedArea = mock(RecommendedArea.class);
        RecommendationRun recommendationRun = mock(RecommendationRun.class);

        given(recommendedAreaRepository.findById(recommendedAreaId))
                .willReturn(Optional.of(recommendedArea));

        given(recommendedArea.getRecommendationRunId())
                .willReturn(recommendationRunId);

        given(recommendationRunRepository.findById(recommendationRunId))
                .willReturn(Optional.of(recommendationRun));

        given(recommendationRun.getMeetingId())
                .willReturn(meetingId);

        given(recommendedPlaceRepository
                .existsByRecommendedAreaId(recommendedAreaId))
                .willReturn(false);

        RecommendedPlace recommendedPlace =
                RecommendedPlace.create(
                        recommendedAreaId,
                        "google-place-id",
                        2
                );

        given(recommendedPlaceRepository
                .findAllByRecommendedAreaId(recommendedAreaId))
                .willReturn(List.of(recommendedPlace));

        GooglePlaceDetailsResponse details =
                new GooglePlaceDetailsResponse(
                        new GooglePlaceDetailsResponse.LocalizedText(
                                "다몽집",
                                "ko"
                        ),
                        new GooglePlaceDetailsResponse.LocalizedText(
                                "한식",
                                "ko"
                        ),
                        "서울특별시 강남구 테헤란로 123",
                        List.of()
                );

        given(googlePlacesClient.getPlaceSummaryDetails("google-place-id"))
                .willReturn(details);

        RecommendedPlaceListResponse response =
                recommendationService.getRecommendedPlaces(
                        meetingId,
                        recommendedAreaId
                );

        assertThat(response.places()).hasSize(1);
        assertThat(response.places().get(0).name())
                .isEqualTo("다몽집");

        verify(placeRecommendationGenerationService)
                .recommend(recommendedAreaId);
    }
}
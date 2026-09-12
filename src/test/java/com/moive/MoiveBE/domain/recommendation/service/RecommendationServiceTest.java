package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.meeting.entity.Participant;
import com.moive.MoiveBE.domain.meeting.entity.ParticipantPreference;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantPreferenceRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantRepository;
import com.moive.MoiveBE.domain.recommendation.client.GooglePlacesClient;
import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceDetailsResponse;
import com.moive.MoiveBE.domain.recommendation.dto.GoogleRouteMatrixResponse;
import com.moive.MoiveBE.domain.recommendation.dto.PlaceRouteResult;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private ParticipantRepository participantRepository;

    @Mock
    private ParticipantPreferenceRepository participantPreferenceRepository;

    @Mock
    private PlaceRouteService placeRouteService;

    @Mock
    private RouteMatrixService routeMatrixService;

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

        Participant participant1 = mock(Participant.class);
        Participant participant2 = mock(Participant.class);
        Participant participant3 = mock(Participant.class);
        Participant participant4 = mock(Participant.class);

        given(participant1.getId()).willReturn(1L);
        given(participant2.getId()).willReturn(2L);
        given(participant3.getId()).willReturn(3L);
        given(participant4.getId()).willReturn(4L);

        given(participantRepository
                .findAllByMeetingIdAndLeftAtIsNull(meetingId))
                .willReturn(List.of(
                        participant1,
                        participant2,
                        participant3,
                        participant4
                ));

        ParticipantPreference preference1 = mock(ParticipantPreference.class);
        ParticipantPreference preference2 = mock(ParticipantPreference.class);
        ParticipantPreference preference3 = mock(ParticipantPreference.class);
        ParticipantPreference preference4 = mock(ParticipantPreference.class);

        given(preference1.getParticipantId()).willReturn(1L);
        given(preference2.getParticipantId()).willReturn(2L);
        given(preference3.getParticipantId()).willReturn(3L);
        given(preference4.getParticipantId()).willReturn(4L);

        given(participantPreferenceRepository
                .findAllByParticipantIdIn(
                        List.of(1L, 2L, 3L, 4L)
                ))
                .willReturn(List.of(
                        preference1,
                        preference2,
                        preference3,
                        preference4
                ));

        RecommendedPlace recommendedPlace =
                RecommendedPlace.create(
                        recommendedAreaId,
                        "google-place-id",
                        "한식",
                        3
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
                        "서울특별시 강남구 테헤란로 123",
                        List.of(),
                        new GooglePlaceDetailsResponse.Location(
                                37.4979,
                                127.0276
                        )
                );

        given(googlePlacesClient
                .getPlaceSummaryDetails("google-place-id"))
                .willReturn(details);

        List<GoogleRouteMatrixResponse> routeResponses =
                List.of(mock(GoogleRouteMatrixResponse.class));

        given(placeRouteService.calculate(
                anyList(),
                anyList()
        )).willReturn(routeResponses);

        PlaceRouteResult routeResult =
                new PlaceRouteResult(
                        0,
                        1500.0,
                        2400.0
                );

        given(routeMatrixService.calculateRouteResults(
                anyList(),
                eq(routeResponses),
                eq(4)
        )).willReturn(List.of(routeResult));

        RecommendedPlaceListResponse response =
                recommendationService.getRecommendedPlaces(
                        meetingId,
                        recommendedAreaId
                );

        assertThat(response.recommendedAreaId())
                .isEqualTo(recommendedAreaId);

        assertThat(response.places())
                .hasSize(1);

        RecommendedPlaceListResponse.Place place =
                response.places().get(0);

        assertThat(place.name())
                .isEqualTo("다몽집");

        assertThat(place.category())
                .isEqualTo("한식");

        assertThat(place.preferenceMatchCnt())
                .isEqualTo(3);

        assertThat(place.preferenceMatchRate())
                .isEqualTo(75);

        assertThat(place.averageTravelTime())
                .isEqualTo(25);

        assertThat(place.maxTravelTime())
                .isEqualTo(40);
    }

    @Test
    void 추천_장소_상세를_조회한다() {

        Long meetingId = 1L;
        Long recommendedAreaId = 1L;
        Long recommendedPlaceId = 1L;
        Long recommendationRunId = 10L;

        RecommendedArea recommendedArea = mock(RecommendedArea.class);
        RecommendationRun recommendationRun = mock(RecommendationRun.class);

        given(recommendedAreaRepository.findById(recommendedAreaId))
                .willReturn(Optional.of(recommendedArea));

        given(recommendedArea.getRecommendationRunId())
                .willReturn(recommendationRunId);

        given(recommendedArea.getAreaName())
                .willReturn("역삼동");

        given(recommendationRunRepository.findById(recommendationRunId))
                .willReturn(Optional.of(recommendationRun));

        given(recommendationRun.getMeetingId())
                .willReturn(meetingId);

        RecommendedPlace recommendedPlace =
                RecommendedPlace.create(
                        recommendedAreaId,
                        "google-place-id",
                        "한식",
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
                        "서울특별시 강남구 테헤란로 123",
                        List.of(
                                new GooglePlaceDetailsResponse.Photo(
                                        "places/test/photos/1"
                                ),
                                new GooglePlaceDetailsResponse.Photo(
                                        "places/test/photos/2"
                                ),
                                new GooglePlaceDetailsResponse.Photo(
                                        "places/test/photos/3"
                                )
                        ),
                        new GooglePlaceDetailsResponse.Location(
                                37.4979,
                                127.0276
                        )
                );

        given(googlePlacesClient.getPlaceDetails("google-place-id"))
                .willReturn(details);

        Participant participant1 = mock(Participant.class);
        Participant participant2 = mock(Participant.class);

        given(participant1.getId()).willReturn(1L);
        given(participant2.getId()).willReturn(2L);

        given(participantRepository
                .findAllByMeetingIdAndLeftAtIsNull(meetingId))
                .willReturn(List.of(
                        participant1,
                        participant2
                ));

        ParticipantPreference preference1 = mock(ParticipantPreference.class);
        ParticipantPreference preference2 = mock(ParticipantPreference.class);

        given(preference1.getParticipantId()).willReturn(1L);
        given(preference2.getParticipantId()).willReturn(2L);

        given(participantPreferenceRepository
                .findAllByParticipantIdIn(
                        List.of(1L, 2L)
                ))
                .willReturn(List.of(
                        preference1,
                        preference2
                ));

        List<GoogleRouteMatrixResponse> routeResponses =
                List.of(mock(GoogleRouteMatrixResponse.class));

        given(placeRouteService.calculate(
                anyList(),
                anyList()
        )).willReturn(routeResponses);

        PlaceRouteResult routeResult =
                new PlaceRouteResult(
                        0,
                        1800.0,
                        2400.0
                );

        given(routeMatrixService.calculateRouteResults(
                anyList(),
                eq(routeResponses),
                eq(2)
        )).willReturn(List.of(routeResult));

        given(googlePlacesClient
                .getPlacePhotoUrl("places/test/photos/1"))
                .willReturn("https://example.com/photo1.jpg");

        given(googlePlacesClient
                .getPlacePhotoUrl("places/test/photos/2"))
                .willReturn("https://example.com/photo2.jpg");

        given(googlePlacesClient
                .getPlacePhotoUrl("places/test/photos/3"))
                .willReturn("https://example.com/photo3.jpg");

        RecommendedPlaceDetailResponse response =
                recommendationService.getRecommendedPlaceDetail(
                        meetingId,
                        recommendedAreaId,
                        recommendedPlaceId
                );

        assertThat(response.name())
                .isEqualTo("다몽집");

        assertThat(response.category())
                .isEqualTo("한식");

        assertThat(response.areaName())
                .isEqualTo("역삼동");

        assertThat(response.address())
                .isEqualTo("서울특별시 강남구 테헤란로 123");

        assertThat(response.preferenceMatchCnt())
                .isEqualTo(3);

        assertThat(response.averageTravelTime())
                .isEqualTo(30);

        assertThat(response.imageUrls()).containsExactly(
                "https://example.com/photo1.jpg",
                "https://example.com/photo2.jpg",
                "https://example.com/photo3.jpg"
        );
    }

    @Test
    void 존재하지_않는_추천_장소를_조회하면_예외가_발생한다() {

        Long meetingId = 1L;
        Long recommendedAreaId = 1L;
        Long recommendedPlaceId = 999L;
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

        given(recommendedPlaceRepository.findById(recommendedPlaceId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                recommendationService.getRecommendedPlaceDetail(
                        meetingId,
                        recommendedAreaId,
                        recommendedPlaceId
                )
        )
                .isInstanceOf(CustomException.class);
    }

    @Test
    void 다른_추천_지역의_장소를_조회하면_예외가_발생한다() {

        Long meetingId = 1L;
        Long requestedAreaId = 1L;
        Long recommendedPlaceId = 1L;
        Long recommendationRunId = 10L;

        RecommendedArea recommendedArea = mock(RecommendedArea.class);
        RecommendationRun recommendationRun = mock(RecommendationRun.class);

        given(recommendedAreaRepository.findById(requestedAreaId))
                .willReturn(Optional.of(recommendedArea));

        given(recommendedArea.getRecommendationRunId())
                .willReturn(recommendationRunId);

        given(recommendationRunRepository.findById(recommendationRunId))
                .willReturn(Optional.of(recommendationRun));

        given(recommendationRun.getMeetingId())
                .willReturn(meetingId);

        RecommendedPlace recommendedPlace =
                RecommendedPlace.create(
                        2L,
                        "google-place-id",
                        "한식",
                        3
                );

        given(recommendedPlaceRepository.findById(recommendedPlaceId))
                .willReturn(Optional.of(recommendedPlace));

        assertThatThrownBy(() ->
                recommendationService.getRecommendedPlaceDetail(
                        meetingId,
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

        Participant participant1 = mock(Participant.class);
        Participant participant2 = mock(Participant.class);

        given(participant1.getId()).willReturn(1L);
        given(participant2.getId()).willReturn(2L);

        given(participantRepository
                .findAllByMeetingIdAndLeftAtIsNull(meetingId))
                .willReturn(List.of(
                        participant1,
                        participant2
                ));

        ParticipantPreference preference1 = mock(ParticipantPreference.class);
        ParticipantPreference preference2 = mock(ParticipantPreference.class);

        given(preference1.getParticipantId()).willReturn(1L);
        given(preference2.getParticipantId()).willReturn(2L);

        given(participantPreferenceRepository
                .findAllByParticipantIdIn(
                        List.of(1L, 2L)
                ))
                .willReturn(List.of(
                        preference1,
                        preference2
                ));

        RecommendedPlace recommendedPlace =
                RecommendedPlace.create(
                        recommendedAreaId,
                        "google-place-id",
                        "한식",
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
                        "서울특별시 강남구 테헤란로 123",
                        List.of(),
                        new GooglePlaceDetailsResponse.Location(
                                37.4979,
                                127.0276
                        )
                );

        given(googlePlacesClient
                .getPlaceSummaryDetails("google-place-id"))
                .willReturn(details);

        List<GoogleRouteMatrixResponse> routeResponses =
                List.of(mock(GoogleRouteMatrixResponse.class));

        given(placeRouteService.calculate(
                anyList(),
                anyList()
        )).willReturn(routeResponses);

        PlaceRouteResult routeResult =
                new PlaceRouteResult(
                        0,
                        1200.0,
                        1800.0
                );

        given(routeMatrixService.calculateRouteResults(
                anyList(),
                eq(routeResponses),
                eq(2)
        )).willReturn(List.of(routeResult));

        RecommendedPlaceListResponse response =
                recommendationService.getRecommendedPlaces(
                        meetingId,
                        recommendedAreaId
                );

        assertThat(response.places())
                .hasSize(1);

        RecommendedPlaceListResponse.Place place =
                response.places().get(0);

        assertThat(place.name())
                .isEqualTo("다몽집");

        assertThat(place.category())
                .isEqualTo("한식");

        assertThat(place.preferenceMatchRate())
                .isEqualTo(100);

        assertThat(place.averageTravelTime())
                .isEqualTo(20);

        assertThat(place.maxTravelTime())
                .isEqualTo(30);

        verify(placeRecommendationGenerationService)
                .recommend(recommendedAreaId);
    }
}
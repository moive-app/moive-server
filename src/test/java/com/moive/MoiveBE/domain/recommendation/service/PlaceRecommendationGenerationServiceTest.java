package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.meeting.entity.Participant;
import com.moive.MoiveBE.domain.meeting.entity.ParticipantPreference;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantPreferenceRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantRepository;
import com.moive.MoiveBE.domain.recommendation.dto.*;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendedArea;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendedPlace;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendationRun;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendedAreaRepository;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendedPlaceRepository;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendationRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaceRecommendationGenerationServiceTest {

    @Mock
    private RecommendedAreaRepository recommendedAreaRepository;

    @Mock
    private RecommendationRunRepository recommendationRunRepository;

    @Mock
    private RecommendedPlaceRepository recommendedPlaceRepository;

    @Mock
    private ParticipantRepository participantRepository;

    @Mock
    private ParticipantPreferenceRepository participantPreferenceRepository;

    @Mock
    private ParticipantRecommendationConditionService
            participantRecommendationConditionService;

    @Mock
    private PlacePreferenceAllocationService placePreferenceAllocationService;

    @Mock
    private PlaceCandidateGenerationService placeCandidateGenerationService;

    @Mock
    private PlaceRouteService placeRouteService;

    @Mock
    private RouteMatrixService routeMatrixService;

    @Mock
    private PlaceMatchService placeMatchService;

    @Mock
    private PlaceRankingService placeRankingService;

    @InjectMocks
    private PlaceRecommendationGenerationService service;

    @Test
    void 추천지역을_기준으로_TOP3_장소를_생성하고_저장한다() {

        // given
        Long recommendedAreaId = 10L;

        RecommendedArea recommendedArea = mock(RecommendedArea.class);
        RecommendationRun recommendationRun = mock(RecommendationRun.class);

        Participant participant1 = mock(Participant.class);
        Participant participant2 = mock(Participant.class);

        ParticipantPreference preference1 =
                mock(ParticipantPreference.class);
        ParticipantPreference preference2 =
                mock(ParticipantPreference.class);

        when(recommendedArea.getRecommendationRunId()).thenReturn(20L);
        when(recommendedArea.getAreaName()).thenReturn("강남역");

        when(recommendationRun.getMeetingId()).thenReturn(30L);

        when(participant1.getId()).thenReturn(1L);
        when(participant2.getId()).thenReturn(2L);

        when(preference1.getParticipantId()).thenReturn(1L);
        when(preference2.getParticipantId()).thenReturn(2L);

        when(recommendedAreaRepository.findById(recommendedAreaId))
                .thenReturn(java.util.Optional.of(recommendedArea));

        when(recommendationRunRepository.findById(20L))
                .thenReturn(java.util.Optional.of(recommendationRun));

        when(participantRepository.findAllByMeetingIdAndLeftAtIsNull(30L))
                .thenReturn(List.of(participant1, participant2));

        /*
         * 일부러 Participant 순서와 반대로 반환한다.
         * 서비스가 participantId 기준으로 다시 정렬하는지 함께 검증한다.
         */
        when(participantPreferenceRepository.findAllByParticipantIdIn(
                List.of(1L, 2L)
        )).thenReturn(
                List.of(preference2, preference1)
        );

        List<ParticipantRecommendationCondition> conditions = List.of(
                new ParticipantRecommendationCondition(
                        0,
                        Set.of("한식"),
                        30
                ),
                new ParticipantRecommendationCondition(
                        1,
                        Set.of("볼링"),
                        60
                )
        );

        when(participantRecommendationConditionService.createConditions(
                List.of(preference1, preference2)
        )).thenReturn(conditions);

        Map<String, Integer> allocations = Map.of(
                "한식", 5,
                "볼링", 5
        );

        when(placePreferenceAllocationService.allocate(conditions))
                .thenReturn(allocations);

        /*
         * 각 장소 후보는 하나의 preferenceType만 가진다.
         *
         * 해당 값은 장소 후보를 검색할 때 사용한
         * MOIVE 활동 카테고리이다.
         */
        List<PlaceCandidate> candidates = List.of(
                new PlaceCandidate(
                        "place-1",
                        37.1,
                        127.1,
                        0,
                        "한식"
                ),
                new PlaceCandidate(
                        "place-2",
                        37.2,
                        127.2,
                        1,
                        "볼링"
                ),
                new PlaceCandidate(
                        "place-3",
                        37.3,
                        127.3,
                        2,
                        "한식"
                )
        );

        when(placeCandidateGenerationService.generateCandidates(
                "강남역",
                allocations
        )).thenReturn(candidates);

        List<GoogleRouteMatrixResponse> routeResponses =
                mock(List.class);

        when(placeRouteService.calculate(
                List.of(preference1, preference2),
                candidates
        )).thenReturn(routeResponses);

        List<PlaceRouteResult> routeResults = List.of(
                new PlaceRouteResult(0, 1000.0, 1200.0),
                new PlaceRouteResult(1, 1100.0, 1300.0),
                new PlaceRouteResult(2, 1200.0, 1400.0)
        );

        when(routeMatrixService.calculateRouteResults(
                candidates,
                routeResponses,
                2
        )).thenReturn(routeResults);

        List<PlaceMatchResult> matchResults = List.of(
                new PlaceMatchResult(0, 2),
                new PlaceMatchResult(1, 1),
                new PlaceMatchResult(2, 2)
        );

        when(placeMatchService.calculateMatchCounts(
                candidates,
                conditions,
                routeResponses
        )).thenReturn(matchResults);

        List<PlaceRankingResult> topPlaces = List.of(
                new PlaceRankingResult(
                        "place-1",
                        0,
                        0,
                        2,
                        1000.0,
                        1200.0
                ),
                new PlaceRankingResult(
                        "place-3",
                        2,
                        2,
                        2,
                        1200.0,
                        1400.0
                ),
                new PlaceRankingResult(
                        "place-2",
                        1,
                        1,
                        1,
                        1100.0,
                        1300.0
                )
        );

        when(placeRankingService.selectTopPlaces(
                candidates,
                routeResults,
                matchResults
        )).thenReturn(topPlaces);

        when(recommendedPlaceRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        List<RecommendedPlace> result =
                service.recommend(recommendedAreaId);

        // then
        assertThat(result).hasSize(3);

        assertThat(result)
                .extracting(RecommendedPlace::getGooglePlaceId)
                .containsExactly(
                        "place-1",
                        "place-3",
                        "place-2"
                );

        assertThat(result)
                .extracting(RecommendedPlace::getCategory)
                .containsExactly(
                        "한식",
                        "한식",
                        "볼링"
                );

        assertThat(result)
                .extracting(RecommendedPlace::getPreferenceMatchCnt)
                .containsExactly(
                        2,
                        2,
                        1
                );

        verify(participantRecommendationConditionService)
                .createConditions(
                        List.of(preference1, preference2)
                );

        verify(placeRouteService).calculate(
                List.of(preference1, preference2),
                candidates
        );

        verify(recommendedPlaceRepository)
                .saveAll(anyList());
    }
}
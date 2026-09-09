package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.meeting.entity.Participant;
import com.moive.MoiveBE.domain.meeting.entity.ParticipantPreference;
import com.moive.MoiveBE.domain.meeting.entity.ParticipantState;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantPreferenceRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantRepository;
import com.moive.MoiveBE.domain.recommendation.dto.*;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendationRun;
import com.moive.MoiveBE.global.exception.CustomException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class AreaRecommendationServiceTest {

    @Test
    void 추천_지역_TOP3를_계산하고_저장한다() {

        ParticipantRepository participantRepository =
                mock(ParticipantRepository.class);

        ParticipantPreferenceRepository participantPreferenceRepository =
                mock(ParticipantPreferenceRepository.class);

        AreaCenterService areaCenterService =
                mock(AreaCenterService.class);

        AreaCandidateGenerationService areaCandidateGenerationService =
                mock(AreaCandidateGenerationService.class);

        AreaRouteService areaRouteService =
                mock(AreaRouteService.class);

        RouteMatrixService routeMatrixService =
                mock(RouteMatrixService.class);

        AreaScoreService areaScoreService =
                mock(AreaScoreService.class);

        AreaRecommendationSaveService areaRecommendationSaveService =
                mock(AreaRecommendationSaveService.class);

        AreaRecommendationService service =
                new AreaRecommendationService(
                        participantRepository,
                        participantPreferenceRepository,
                        areaCenterService,
                        areaCandidateGenerationService,
                        areaRouteService,
                        routeMatrixService,
                        areaScoreService,
                        areaRecommendationSaveService
                );

        Participant participant1 =
                Participant.create(
                        1L,
                        10L,
                        ParticipantState.COND_DONE
                );

        Participant participant2 =
                Participant.create(
                        1L,
                        20L,
                        ParticipantState.COND_DONE
                );

        List<ParticipantPreference> preferences = List.of(
                ParticipantPreference.create(
                        1L,
                        "강남역",
                        BigDecimal.valueOf(37.4979),
                        BigDecimal.valueOf(127.0276),
                        60
                ),
                ParticipantPreference.create(
                        2L,
                        "잠실역",
                        BigDecimal.valueOf(37.5133),
                        BigDecimal.valueOf(127.1001),
                        60
                )
        );

        AreaCenter center =
                new AreaCenter(
                        37.5056,
                        127.0638
                );

        List<AreaCandidate> candidates = List.of(
                new AreaCandidate(
                        "역삼동",
                        "place-1",
                        37.5006,
                        127.0363
                ),
                new AreaCandidate(
                        "논현동",
                        "place-2",
                        37.5112,
                        127.0285
                ),
                new AreaCandidate(
                        "신사동",
                        "place-3",
                        37.5160,
                        127.0200
                )
        );

        List<GoogleRouteMatrixResponse> routeResponses =
                List.of();

        List<AreaRouteResult> routeResults = List.of(
                new AreaRouteResult(
                        0,
                        1200.0,
                        1500.0,
                        100.0
                ),
                new AreaRouteResult(
                        1,
                        1300.0,
                        1600.0,
                        200.0
                ),
                new AreaRouteResult(
                        2,
                        1400.0,
                        1700.0,
                        300.0
                )
        );

        List<AreaScoreResult> scoreResults = List.of(
                new AreaScoreResult(
                        0,
                        0.0,
                        0.0,
                        0.0,
                        0.0
                ),
                new AreaScoreResult(
                        1,
                        0.5,
                        0.5,
                        0.5,
                        0.5
                ),
                new AreaScoreResult(
                        2,
                        1.0,
                        1.0,
                        1.0,
                        1.0
                )
        );

        List<AreaScoreResult> top3 =
                scoreResults;

        RecommendationRun savedRun =
                RecommendationRun.create(1L);

        when(participantRepository
                .findAllByMeetingIdAndLeftAtIsNull(1L))
                .thenReturn(
                        List.of(
                                participant1,
                                participant2
                        )
                );

        when(participantPreferenceRepository
                .findAllByParticipantIdIn(anyList()))
                .thenReturn(preferences);

        when(areaCenterService.calculate(preferences))
                .thenReturn(center);

        when(areaCandidateGenerationService.generate(center))
                .thenReturn(candidates);

        when(areaRouteService.calculate(
                preferences,
                candidates
        )).thenReturn(routeResponses);

        when(routeMatrixService.calculateAreaRouteResults(
                candidates,
                routeResponses,
                preferences.size()
        )).thenReturn(routeResults);

        when(areaScoreService.calculateScores(routeResults))
                .thenReturn(scoreResults);

        when(areaScoreService.selectTop3(scoreResults))
                .thenReturn(top3);

        when(areaRecommendationSaveService.save(
                1L,
                candidates,
                top3
        )).thenReturn(savedRun);

        RecommendationRun result =
                service.recommend(1L);

        assertThat(result)
                .isSameAs(savedRun);

        verify(areaCenterService)
                .calculate(preferences);

        verify(areaCandidateGenerationService)
                .generate(center);

        verify(areaRouteService)
                .calculate(
                        preferences,
                        candidates
                );

        verify(routeMatrixService)
                .calculateAreaRouteResults(
                        candidates,
                        routeResponses,
                        preferences.size()
                );

        verify(areaScoreService)
                .calculateScores(routeResults);

        verify(areaScoreService)
                .selectTop3(scoreResults);

        verify(areaRecommendationSaveService)
                .save(
                        1L,
                        candidates,
                        top3
                );
    }

    @Test
    void 이동_가능한_추천_후보가_3개_미만이면_예외가_발생한다() {

        ParticipantRepository participantRepository =
                mock(ParticipantRepository.class);

        ParticipantPreferenceRepository participantPreferenceRepository =
                mock(ParticipantPreferenceRepository.class);

        AreaCenterService areaCenterService =
                mock(AreaCenterService.class);

        AreaCandidateGenerationService areaCandidateGenerationService =
                mock(AreaCandidateGenerationService.class);

        AreaRouteService areaRouteService =
                mock(AreaRouteService.class);

        RouteMatrixService routeMatrixService =
                mock(RouteMatrixService.class);

        AreaScoreService areaScoreService =
                mock(AreaScoreService.class);

        AreaRecommendationSaveService areaRecommendationSaveService =
                mock(AreaRecommendationSaveService.class);

        AreaRecommendationService service =
                new AreaRecommendationService(
                        participantRepository,
                        participantPreferenceRepository,
                        areaCenterService,
                        areaCandidateGenerationService,
                        areaRouteService,
                        routeMatrixService,
                        areaScoreService,
                        areaRecommendationSaveService
                );

        Participant participant1 =
                Participant.create(
                        1L,
                        10L,
                        ParticipantState.COND_DONE
                );

        Participant participant2 =
                Participant.create(
                        1L,
                        20L,
                        ParticipantState.COND_DONE
                );

        List<ParticipantPreference> preferences = List.of(
                ParticipantPreference.create(
                        1L,
                        "강남역",
                        BigDecimal.valueOf(37.4979),
                        BigDecimal.valueOf(127.0276),
                        60
                ),
                ParticipantPreference.create(
                        2L,
                        "잠실역",
                        BigDecimal.valueOf(37.5133),
                        BigDecimal.valueOf(127.1001),
                        60
                )
        );

        AreaCenter center =
                new AreaCenter(
                        37.5056,
                        127.0638
                );

        List<AreaCandidate> candidates = List.of(
                new AreaCandidate(
                        "역삼동",
                        "place-1",
                        37.5006,
                        127.0363
                ),
                new AreaCandidate(
                        "논현동",
                        "place-2",
                        37.5112,
                        127.0285
                ),
                new AreaCandidate(
                        "신사동",
                        "place-3",
                        37.5160,
                        127.0200
                )
        );

        List<GoogleRouteMatrixResponse> routeResponses =
                List.of();

        List<AreaRouteResult> routeResults = List.of(
                new AreaRouteResult(
                        0,
                        1200.0,
                        1500.0,
                        100.0
                ),
                new AreaRouteResult(
                        1,
                        1300.0,
                        1600.0,
                        200.0
                )
        );

        when(participantRepository
                .findAllByMeetingIdAndLeftAtIsNull(1L))
                .thenReturn(
                        List.of(
                                participant1,
                                participant2
                        )
                );

        when(participantPreferenceRepository
                .findAllByParticipantIdIn(anyList()))
                .thenReturn(preferences);

        when(areaCenterService.calculate(preferences))
                .thenReturn(center);

        when(areaCandidateGenerationService.generate(center))
                .thenReturn(candidates);

        when(areaRouteService.calculate(
                preferences,
                candidates
        )).thenReturn(routeResponses);

        when(routeMatrixService.calculateAreaRouteResults(
                candidates,
                routeResponses,
                preferences.size()
        )).thenReturn(routeResults);

        assertThatThrownBy(
                () -> service.recommend(1L)
        ).isInstanceOf(CustomException.class);

        verifyNoInteractions(
                areaScoreService,
                areaRecommendationSaveService
        );
    }
}
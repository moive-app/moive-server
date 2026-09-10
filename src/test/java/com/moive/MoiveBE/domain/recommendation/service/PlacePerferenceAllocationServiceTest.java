package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.dto.ParticipantRecommendationCondition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlacePreferenceAllocationServiceTest {

    @Mock
    private CandidateAllocationService candidateAllocationService;

    @InjectMocks
    private PlacePreferenceAllocationService service;

    @Test
    void 참가자_선호활동의_선택횟수를_계산하고_후보개수를_배분한다() {

        // given
        List<ParticipantRecommendationCondition> participants = List.of(
                new ParticipantRecommendationCondition(
                        0,
                        Set.of("한식", "볼링"),
                        30
                ),
                new ParticipantRecommendationCondition(
                        1,
                        Set.of("한식"),
                        60
                ),
                new ParticipantRecommendationCondition(
                        2,
                        Set.of("한식", "방탈출"),
                        30
                )
        );

        Map<String, Integer> expectedAllocations = Map.of(
                "한식", 6,
                "볼링", 2,
                "방탈출", 2
        );

        when(candidateAllocationService.allocate(
                Map.of(
                        "한식", 3,
                        "볼링", 1,
                        "방탈출", 1
                )
        )).thenReturn(expectedAllocations);

        // when
        Map<String, Integer> result =
                service.allocate(participants);

        // then
        assertThat(result).isEqualTo(expectedAllocations);

        verify(candidateAllocationService).allocate(
                Map.of(
                        "한식", 3,
                        "볼링", 1,
                        "방탈출", 1
                )
        );
    }
}
package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.meeting.entity.Activity;
import com.moive.MoiveBE.domain.meeting.entity.ActivityType;
import com.moive.MoiveBE.domain.meeting.entity.ParticipantPreference;
import com.moive.MoiveBE.domain.meeting.entity.PreferenceActivity;
import com.moive.MoiveBE.domain.meeting.repository.ActivityRepository;
import com.moive.MoiveBE.domain.meeting.repository.PreferenceActivityRepository;
import com.moive.MoiveBE.domain.recommendation.dto.ParticipantRecommendationCondition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParticipantRecommendationConditionServiceTest {

    @Mock
    private PreferenceActivityRepository preferenceActivityRepository;

    @Mock
    private ActivityRepository activityRepository;

    @InjectMocks
    private ParticipantRecommendationConditionService service;

    @Test
    void 참가자_선호조건을_추천계산용_조건으로_변환한다() {

        // given
        ParticipantPreference preference1 =
                mock(ParticipantPreference.class);
        ParticipantPreference preference2 =
                mock(ParticipantPreference.class);

        when(preference1.getId()).thenReturn(100L);
        when(preference1.getMaxTravelMinutes()).thenReturn(30);

        when(preference2.getId()).thenReturn(200L);
        when(preference2.getMaxTravelMinutes()).thenReturn(60);

        PreferenceActivity preferenceActivity1 =
                mock(PreferenceActivity.class);
        PreferenceActivity preferenceActivity2 =
                mock(PreferenceActivity.class);
        PreferenceActivity preferenceActivity3 =
                mock(PreferenceActivity.class);

        when(preferenceActivity1.getPreferenceId()).thenReturn(100L);
        when(preferenceActivity1.getActivityId()).thenReturn(1L);

        when(preferenceActivity2.getPreferenceId()).thenReturn(100L);
        when(preferenceActivity2.getActivityId()).thenReturn(2L);

        when(preferenceActivity3.getPreferenceId()).thenReturn(200L);
        when(preferenceActivity3.getActivityId()).thenReturn(1L);

        Activity koreanFood = mock(Activity.class);
        Activity bowling = mock(Activity.class);

        when(koreanFood.getId()).thenReturn(1L);
        when(koreanFood.getName()).thenReturn(ActivityType.KOREAN_FOOD);

        when(bowling.getId()).thenReturn(2L);
        when(bowling.getName()).thenReturn(ActivityType.BOWLING);

        when(preferenceActivityRepository.findAllByPreferenceIdIn(
                List.of(100L, 200L)
        )).thenReturn(
                List.of(
                        preferenceActivity1,
                        preferenceActivity2,
                        preferenceActivity3
                )
        );

        when(activityRepository.findAllById(
                List.of(1L, 2L)
        )).thenReturn(
                List.of(koreanFood, bowling)
        );

        // when
        List<ParticipantRecommendationCondition> result =
                service.createConditions(
                        List.of(preference1, preference2)
                );

        // then
        assertThat(result).hasSize(2);

        assertThat(result.get(0).originIndex()).isEqualTo(0);
        assertThat(result.get(0).maxTravelMinutes()).isEqualTo(30);
        assertThat(result.get(0).preferenceTypes())
                .containsExactlyInAnyOrder("한식", "볼링");

        assertThat(result.get(1).originIndex()).isEqualTo(1);
        assertThat(result.get(1).maxTravelMinutes()).isEqualTo(60);
        assertThat(result.get(1).preferenceTypes())
                .containsExactly("한식");
    }
}
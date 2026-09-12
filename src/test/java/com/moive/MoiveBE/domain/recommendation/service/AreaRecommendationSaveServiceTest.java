package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.dto.AreaCandidate;
import com.moive.MoiveBE.domain.recommendation.dto.AreaScoreResult;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendationRun;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendationStatus;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendedArea;
import com.moive.MoiveBE.domain.meeting.entity.Participant;
import com.moive.MoiveBE.domain.meeting.repository.MeetingRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantRepository;
import com.moive.MoiveBE.domain.meeting.entity.Meeting;
import com.moive.MoiveBE.domain.notification.service.NotificationService;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendationRunRepository;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendedAreaRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AreaRecommendationSaveServiceTest {

    @Test
    void 추천_지역_TOP3를_저장하고_추천_실행을_완료한다() {

        RecommendationRunRepository recommendationRunRepository =
                mock(RecommendationRunRepository.class);

        RecommendedAreaRepository recommendedAreaRepository =
                mock(RecommendedAreaRepository.class);

        MeetingRepository meetingRepository = mock(MeetingRepository.class);
        ParticipantRepository participantRepository = mock(ParticipantRepository.class);
        NotificationService notificationService = mock(NotificationService.class);

        Meeting meeting = mock(Meeting.class);
        when(meeting.getName()).thenReturn("테스트 모임");
        when(meetingRepository.findById(1L)).thenReturn(java.util.Optional.of(meeting));
        when(participantRepository.findAllByMeetingIdAndLeftAtIsNullOrderByJoinedAtAsc(1L))
                .thenReturn(List.of());

        AreaRecommendationSaveService service =
                new AreaRecommendationSaveService(
                        recommendationRunRepository,
                        recommendedAreaRepository,
                        meetingRepository,
                        participantRepository,
                        notificationService
                );

        RecommendationRun run =
                RecommendationRun.create(1L);

        when(recommendationRunRepository.save(any()))
                .thenReturn(run);

        List<AreaCandidate> candidates = List.of(
                new AreaCandidate("역삼동", "place-1", 37.5, 127.03),
                new AreaCandidate("논현동", "place-2", 37.51, 127.02),
                new AreaCandidate("신사동", "place-3", 37.52, 127.02)
        );

        List<AreaScoreResult> top3 = List.of(
                new AreaScoreResult(1, 0.1, 0.1, 0.1, 0.1),
                new AreaScoreResult(0, 0.2, 0.2, 0.2, 0.2),
                new AreaScoreResult(2, 0.3, 0.3, 0.3, 0.3)
        );

        service.save(
                1L,
                candidates,
                top3
        );

        ArgumentCaptor<List<RecommendedArea>> captor =
                ArgumentCaptor.forClass(List.class);

        verify(recommendedAreaRepository)
                .saveAll(captor.capture());

        List<RecommendedArea> savedAreas =
                captor.getValue();

        assertThat(savedAreas)
                .extracting(RecommendedArea::getAreaName)
                .containsExactly(
                        "논현동",
                        "역삼동",
                        "신사동"
                );

        assertThat(run.getStatus())
                .isEqualTo(RecommendationStatus.COMPLETED);
    }
}
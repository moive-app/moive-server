package com.moive.MoiveBE.domain.meeting.service;

import com.moive.MoiveBE.domain.meeting.entity.MeetingStatus;
import com.moive.MoiveBE.domain.meeting.repository.ActivityRepository;
import com.moive.MoiveBE.domain.meeting.repository.DateVoteRepository;
import com.moive.MoiveBE.domain.meeting.repository.MeetingPurposeRepository;
import com.moive.MoiveBE.domain.meeting.repository.MeetingRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantPreferenceRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantRepository;
import com.moive.MoiveBE.domain.meeting.repository.PreferenceActivityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeetingServiceCompleteTest {

    @Mock private MeetingRepository meetingRepository;
    @Mock private MeetingPurposeRepository meetingPurposeRepository;
    @Mock private ParticipantRepository participantRepository;
    @Mock private ParticipantPreferenceRepository preferenceRepository;
    @Mock private PreferenceActivityRepository preferenceActivityRepository;
    @Mock private ActivityRepository activityRepository;
    @Mock private DateVoteRepository dateVoteRepository;

    @InjectMocks private MeetingService meetingService;

    @Test
    void 일정이_오늘_이전인_CONFIRMED_모임을_COMPLETED로_일괄_변경하고_변경_건수를_반환한다() {
        // given
        when(meetingRepository.updateStatusByStatusAndScheduledDateBefore(
                eq(MeetingStatus.CONFIRMED), eq(MeetingStatus.COMPLETED), eq(LocalDate.now()), any(LocalDateTime.class)))
                .thenReturn(2);

        // when
        int completedCnt = meetingService.completeElapsedMeetings();

        // then
        assertThat(completedCnt).isEqualTo(2);
    }
}

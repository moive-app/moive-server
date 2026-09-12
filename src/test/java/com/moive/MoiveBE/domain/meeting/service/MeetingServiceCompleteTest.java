package com.moive.MoiveBE.domain.meeting.service;

import com.moive.MoiveBE.domain.meeting.entity.Meeting;
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
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    void 일정이_지난_CONFIRMED_모임을_COMPLETED로_전환한다() {
        // given
        Meeting elapsed = mock(Meeting.class);
        when(meetingRepository.findAllByStatusAndScheduledDateBefore(MeetingStatus.CONFIRMED, LocalDate.now()))
                .thenReturn(List.of(elapsed));

        // when
        meetingService.completeElapsedMeetings();

        // then
        verify(elapsed).complete();
    }

    @Test
    void 대상_모임이_없으면_아무것도_하지_않는다() {
        // given
        when(meetingRepository.findAllByStatusAndScheduledDateBefore(MeetingStatus.CONFIRMED, LocalDate.now()))
                .thenReturn(List.of());

        // when
        meetingService.completeElapsedMeetings();

        // then
        verifyNoInteractions(participantRepository, dateVoteRepository);
    }

    @Test
    void 여러_모임을_한번에_COMPLETED로_전환한다() {
        // given
        Meeting a = mock(Meeting.class);
        Meeting b = mock(Meeting.class);
        when(meetingRepository.findAllByStatusAndScheduledDateBefore(MeetingStatus.CONFIRMED, LocalDate.now()))
                .thenReturn(List.of(a, b));

        // when
        meetingService.completeElapsedMeetings();

        // then
        verify(a).complete();
        verify(b).complete();
    }
}

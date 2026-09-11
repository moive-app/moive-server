package com.moive.MoiveBE.domain.meeting.service;

import com.moive.MoiveBE.domain.meeting.entity.Meeting;
import com.moive.MoiveBE.domain.meeting.entity.MeetingStatus;
import com.moive.MoiveBE.domain.meeting.entity.Participant;
import com.moive.MoiveBE.domain.meeting.entity.ParticipantState;
import com.moive.MoiveBE.domain.meeting.repository.MeetingRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantRepository;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeetingLeaveServiceTest {

    @Mock private MeetingRepository meetingRepository;
    @Mock private ParticipantRepository participantRepository;

    private MeetingLeaveService meetingLeaveService;
    private static final Long CURRENT_USER_ID = 1L;
    private static final Long MEETING_ID = 10L;

    @BeforeEach
    void setUp() {
        meetingLeaveService = new MeetingLeaveService(meetingRepository, participantRepository);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        CURRENT_USER_ID, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 일반_참여자가_나가면_leftAt이_설정되고_참여자_수가_감소한다() {
        Long creatorId = 99L; // 현재 유저는 모임장이 아닌 일반 참여자
        Meeting meeting = mockMeeting(MEETING_ID, MeetingStatus.CONDITION_INPUT, creatorId);
        Participant me = mockParticipant(CURRENT_USER_ID, ParticipantState.COND_PENDING, false);
        Participant other = mockParticipant(2L, ParticipantState.COND_PENDING, false);

        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.of(meeting));
        when(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(MEETING_ID, CURRENT_USER_ID))
                .thenReturn(Optional.of(me));
        when(participantRepository.findAllByMeetingIdAndLeftAtIsNullOrderByJoinedAtAsc(MEETING_ID))
                .thenReturn(List.of(me, other));

        meetingLeaveService.leaveMeeting(MEETING_ID);

        verify(me).leave();
        verify(meeting).decrementParticipantCnt();
        verify(meeting, never()).complete();
        verify(meeting, never()).updateCreator(any());
    }

    @Test
    void 조건입력_완료한_참여자가_나가면_submittedCnt도_감소한다() {
        Meeting meeting = mockMeeting(MEETING_ID, MeetingStatus.CONDITION_INPUT, CURRENT_USER_ID);
        Participant me = mockParticipant(CURRENT_USER_ID, ParticipantState.COND_DONE, true);
        Participant other = mockParticipant(2L, ParticipantState.COND_PENDING, false);

        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.of(meeting));
        when(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(MEETING_ID, CURRENT_USER_ID))
                .thenReturn(Optional.of(me));
        when(participantRepository.findAllByMeetingIdAndLeftAtIsNullOrderByJoinedAtAsc(MEETING_ID))
                .thenReturn(List.of(me, other));

        meetingLeaveService.leaveMeeting(MEETING_ID);

        verify(meeting).decrementSubmittedCnt();
    }

    @Test
    void 모임장이_나가면_가입일시가_가장_빠른_잔여_참여자로_승계된다() {
        Meeting meeting = mockMeeting(MEETING_ID, MeetingStatus.CONDITION_INPUT, CURRENT_USER_ID);
        Participant me = mockParticipant(CURRENT_USER_ID, ParticipantState.COND_PENDING, false);
        Participant next = mockParticipant(2L, ParticipantState.COND_PENDING, false);

        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.of(meeting));
        when(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(MEETING_ID, CURRENT_USER_ID))
                .thenReturn(Optional.of(me));
        when(participantRepository.findAllByMeetingIdAndLeftAtIsNullOrderByJoinedAtAsc(MEETING_ID))
                .thenReturn(List.of(me, next));

        meetingLeaveService.leaveMeeting(MEETING_ID);

        verify(meeting).updateCreator(2L);
    }

    @Test
    void 마지막_참여자가_나가면_모임이_COMPLETED로_전환된다() {
        Meeting meeting = mockMeeting(MEETING_ID, MeetingStatus.CONDITION_INPUT, CURRENT_USER_ID);
        Participant me = mockParticipant(CURRENT_USER_ID, ParticipantState.COND_PENDING, false);

        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.of(meeting));
        when(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(MEETING_ID, CURRENT_USER_ID))
                .thenReturn(Optional.of(me));
        when(participantRepository.findAllByMeetingIdAndLeftAtIsNullOrderByJoinedAtAsc(MEETING_ID))
                .thenReturn(List.of(me));

        meetingLeaveService.leaveMeeting(MEETING_ID);

        verify(meeting).complete();
        verify(me).leave();
        verify(meeting, never()).updateCreator(any());
    }

    @Test
    void 존재하지_않는_모임이면_MEETING_NOT_FOUND_예외가_발생한다() {
        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> meetingLeaveService.leaveMeeting(MEETING_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getCustomErrorCode())
                        .isEqualTo(CustomErrorCode.MEETING_NOT_FOUND));
    }

    @Test
    void COMPLETED_모임에서_나가려하면_CANNOT_LEAVE_COMPLETED_MEETING_예외가_발생한다() {
        Meeting meeting = mockMeeting(MEETING_ID, MeetingStatus.COMPLETED, CURRENT_USER_ID);
        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.of(meeting));

        assertThatThrownBy(() -> meetingLeaveService.leaveMeeting(MEETING_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getCustomErrorCode())
                        .isEqualTo(CustomErrorCode.CANNOT_LEAVE_COMPLETED_MEETING));

        verify(participantRepository, never()).findByMeetingIdAndUserIdAndLeftAtIsNull(any(), any());
    }

    @Test
    void 모임_참여자가_아니면_NOT_A_PARTICIPANT_예외가_발생한다() {
        Meeting meeting = mockMeeting(MEETING_ID, MeetingStatus.CONDITION_INPUT, 99L);
        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.of(meeting));
        when(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(MEETING_ID, CURRENT_USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> meetingLeaveService.leaveMeeting(MEETING_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getCustomErrorCode())
                        .isEqualTo(CustomErrorCode.NOT_A_PARTICIPANT));
    }

    private Meeting mockMeeting(Long id, MeetingStatus status, Long creatorUserId) {
        Meeting meeting = mock(Meeting.class);
        lenient().when(meeting.getId()).thenReturn(id);
        lenient().when(meeting.getStatus()).thenReturn(status);
        lenient().when(meeting.getCreatorUserId()).thenReturn(creatorUserId);
        return meeting;
    }

    private Participant mockParticipant(Long userId, ParticipantState state, boolean conditionCompleted) {
        Participant participant = mock(Participant.class);
        lenient().when(participant.getUserId()).thenReturn(userId);
        lenient().when(participant.getState()).thenReturn(state);
        lenient().when(participant.isConditionCompleted()).thenReturn(conditionCompleted);
        return participant;
    }
}

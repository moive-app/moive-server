package com.moive.MoiveBE.domain.meeting.service;

import com.moive.MoiveBE.domain.meeting.dto.JoinMeetingResponse;
import com.moive.MoiveBE.domain.meeting.entity.MeetingStatus;
import com.moive.MoiveBE.domain.meeting.entity.Participant;
import com.moive.MoiveBE.domain.meeting.entity.ParticipantState;
import com.moive.MoiveBE.domain.meeting.entity.Meeting;
import com.moive.MoiveBE.domain.meeting.repository.*;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeetingServiceJoinTest {

    @Mock private MeetingRepository meetingRepository;
    @Mock private MeetingPurposeRepository meetingPurposeRepository;
    @Mock private ParticipantRepository participantRepository;
    @Mock private ParticipantPreferenceRepository preferenceRepository;
    @Mock private PreferenceActivityRepository preferenceActivityRepository;
    @Mock private ActivityRepository activityRepository;
    @Mock private DateVoteRepository dateVoteRepository;

    private MeetingService meetingService;

    @BeforeEach
    void setUp() {
        meetingService = new MeetingService(
                meetingRepository,
                meetingPurposeRepository,
                participantRepository,
                preferenceRepository,
                preferenceActivityRepository,
                activityRepository,
                dateVoteRepository,
                "https://moive.app/invite"
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        2L, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void CONDITION_INPUT_모임에_신규_참여하면_COND_PENDING_상태로_등록된다() {
        // given
        Meeting meeting = mockMeeting(1L, MeetingStatus.CONDITION_INPUT, 3);

        when(meetingRepository.findByInviteCode("ABC123XY")).thenReturn(Optional.of(meeting));
        when(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(1L, 2L))
                .thenReturn(Optional.empty());

        Participant savedParticipant = mock(Participant.class);
        when(savedParticipant.getId()).thenReturn(5L);
        when(participantRepository.save(any(Participant.class))).thenReturn(savedParticipant);

        // when
        JoinMeetingResponse response = meetingService.joinMeeting("ABC123XY");

        // then
        assertThat(response.meetingId()).isEqualTo(1L);
        assertThat(response.participantId()).isEqualTo(5L);
        assertThat(response.participantState()).isEqualTo("COND_PENDING");
        assertThat(response.alreadyParticipant()).isFalse();

        verify(meeting).incrementParticipantCnt();
    }

    @Test
    void VOTING_모임에_신규_참여하면_NEW_RESTRICTED_상태로_등록된다() {
        // given
        Meeting meeting = mockMeeting(1L, MeetingStatus.VOTING, 5);

        when(meetingRepository.findByInviteCode("CODE1234")).thenReturn(Optional.of(meeting));
        when(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(1L, 2L))
                .thenReturn(Optional.empty());

        Participant savedParticipant = mock(Participant.class);
        when(savedParticipant.getId()).thenReturn(6L);
        when(participantRepository.save(any(Participant.class))).thenReturn(savedParticipant);

        // when
        JoinMeetingResponse response = meetingService.joinMeeting("CODE1234");

        // then
        assertThat(response.participantState()).isEqualTo("NEW_RESTRICTED");
        assertThat(response.alreadyParticipant()).isFalse();
    }

    @Test
    void 이미_참여_중이면_기존_상태를_그대로_반환하고_참여자를_추가하지_않는다() {
        // given
        Meeting meeting = mockMeeting(1L, MeetingStatus.CONDITION_INPUT, 2);

        Participant existing = mock(Participant.class);
        when(existing.getId()).thenReturn(3L);
        when(existing.getState()).thenReturn(ParticipantState.COND_PENDING);

        when(meetingRepository.findByInviteCode("ABC123XY")).thenReturn(Optional.of(meeting));
        when(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(1L, 2L))
                .thenReturn(Optional.of(existing));

        // when
        JoinMeetingResponse response = meetingService.joinMeeting("ABC123XY");

        // then
        assertThat(response.alreadyParticipant()).isTrue();
        assertThat(response.participantId()).isEqualTo(3L);

        verify(participantRepository, never()).save(any());
        verify(meeting, never()).incrementParticipantCnt();
    }

    @Test
    void 유효하지_않은_초대코드면_INVALID_INVITE_CODE_예외가_발생한다() {
        when(meetingRepository.findByInviteCode("WRONGCOD")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> meetingService.joinMeeting("WRONGCOD"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getCustomErrorCode())
                        .isEqualTo(CustomErrorCode.INVALID_INVITE_CODE));
    }

    @Test
    void 정원이_가득_찬_모임이면_MEETING_FULL_예외가_발생한다() {
        Meeting meeting = mockMeeting(1L, MeetingStatus.CONDITION_INPUT, 10);

        when(meetingRepository.findByInviteCode("FULLCODE")).thenReturn(Optional.of(meeting));
        when(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(1L, 2L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> meetingService.joinMeeting("FULLCODE"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getCustomErrorCode())
                        .isEqualTo(CustomErrorCode.MEETING_FULL));

        verify(participantRepository, never()).save(any());
    }

    @Test
    void 완료된_모임이면_MEETING_COMPLETED_예외가_발생한다() {
        Meeting meeting = mockMeeting(1L, MeetingStatus.COMPLETED, 5);

        when(meetingRepository.findByInviteCode("DONEXXXX")).thenReturn(Optional.of(meeting));

        assertThatThrownBy(() -> meetingService.joinMeeting("DONEXXXX"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getCustomErrorCode())
                        .isEqualTo(CustomErrorCode.MEETING_COMPLETED));

        verify(participantRepository, never()).findByMeetingIdAndUserIdAndLeftAtIsNull(any(), any());
    }

    private Meeting mockMeeting(Long id, MeetingStatus status, int participantCnt) {
        Meeting meeting = mock(Meeting.class);
        lenient().when(meeting.getId()).thenReturn(id);
        lenient().when(meeting.getStatus()).thenReturn(status);
        lenient().when(meeting.getParticipantCnt()).thenReturn(participantCnt);
        return meeting;
    }
}

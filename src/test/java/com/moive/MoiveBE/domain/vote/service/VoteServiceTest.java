package com.moive.MoiveBE.domain.vote.service;

import com.moive.MoiveBE.domain.meeting.entity.Meeting;
import com.moive.MoiveBE.domain.meeting.entity.Participant;
import com.moive.MoiveBE.domain.meeting.repository.DateVoteRepository;
import com.moive.MoiveBE.domain.meeting.repository.MeetingRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantRepository;
import com.moive.MoiveBE.domain.vote.dto.DateVoteSummary;
import com.moive.MoiveBE.domain.vote.dto.DateVoteResultResponse;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static com.moive.MoiveBE.global.exception.CustomErrorCode.VOTE_ACCESS_DENIED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoteServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long MEETING_ID = 10L;
    private static final Long MY_PARTICIPANT_ID = 100L;

    @Mock private MeetingRepository meetingRepository;
    @Mock private ParticipantRepository participantRepository;
    @Mock private DateVoteRepository dateVoteRepository;

    @InjectMocks private VoteService voteService;

    @Test
    void 유저가_모임_참여자가_아니면_VOTE_ACCESS_DENIED() {
        // given
        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.of(mock(Meeting.class)));
        when(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(MEETING_ID, USER_ID))
                .thenReturn(Optional.empty());

        // when & then
        assertErrorCode(() -> voteService.getMeetingScheduleVoteResult(USER_ID, MEETING_ID), VOTE_ACCESS_DENIED);
        verifyNoInteractions(dateVoteRepository);
    }

    @Test
    void 모임_생성_시_일정이_확정된_경우_투표가_생략되고_확정_일정_1건만_반환한다() {
        // given
        LocalDate scheduledDate = LocalDate.of(2026, 10, 10);
        LocalTime scheduledTime = LocalTime.of(18, 0);
        Meeting meeting = mock(Meeting.class);
        when(meeting.getScheduledDate()).thenReturn(scheduledDate);
        when(meeting.getScheduledTime()).thenReturn(scheduledTime);
        stubMeetingAndParticipant(meeting);

        // when
        DateVoteResultResponse response = voteService.getMeetingScheduleVoteResult(USER_ID, MEETING_ID);

        // then
        assertThat(response.isVoteSkipped()).isTrue();
        assertThat(response.totalVoterCnt()).isNull();
        assertThat(response.candidates()).hasSize(1);

        DateVoteResultResponse.Candidate candidate = response.candidates().get(0);
        assertThat(candidate.meetingDate()).isEqualTo(scheduledDate);
        assertThat(candidate.meetingTime()).isEqualTo(scheduledTime);
        assertThat(candidate.voterCnt()).isNull();
        assertThat(candidate.isVotedByMe()).isFalse();

        // 투표 집계 진행 x
        verifyNoInteractions(dateVoteRepository);
    }

    @Test
    void 일정_미확정이면_집계_결과를_순서_그대로_candidate로_매핑하고_상위_3건을_요청한다() {
        // given
        Meeting meeting = mock(Meeting.class);
        stubMeetingAndParticipant(meeting, MY_PARTICIPANT_ID);

        when(dateVoteRepository.countDistinctVoters(MEETING_ID)).thenReturn(4L);
        when(dateVoteRepository.aggregateTopDates(eq(MEETING_ID), eq(MY_PARTICIPANT_ID), any()))
                .thenReturn(List.of(
                        new DateVoteSummary(LocalDate.of(2026, 9, 15), LocalTime.of(18, 0), 4L, 1L),
                        new DateVoteSummary(LocalDate.of(2026, 9, 12), LocalTime.of(15, 0), 2L, 0L),
                        new DateVoteSummary(LocalDate.of(2026, 9, 13), LocalTime.of(14, 0), 2L, null)
                ));

        // when
        DateVoteResultResponse response = voteService.getMeetingScheduleVoteResult(USER_ID, MEETING_ID);

        // then
        assertThat(response.isVoteSkipped()).isFalse();
        assertThat(response.totalVoterCnt()).isEqualTo(4);
        assertThat(response.candidates()).extracting(
                DateVoteResultResponse.Candidate::meetingDate,
                DateVoteResultResponse.Candidate::meetingTime,
                DateVoteResultResponse.Candidate::voterCnt,
                DateVoteResultResponse.Candidate::isVotedByMe
        ).containsExactly(
                tuple(LocalDate.of(2026, 9, 15), LocalTime.of(18, 0), 4, true),
                tuple(LocalDate.of(2026, 9, 12), LocalTime.of(15, 0), 2, false),
                tuple(LocalDate.of(2026, 9, 13), LocalTime.of(14, 0), 2, false)
        );

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(dateVoteRepository).aggregateTopDates(eq(MEETING_ID), eq(MY_PARTICIPANT_ID), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(3);
    }

    @Test
    void 일정_미확정_모임에_투표가_없으면_totalVoterCnt는_0이고_candidates는_빈_리스트다() {
        // given
        Meeting meeting = mock(Meeting.class);
        stubMeetingAndParticipant(meeting);
        when(dateVoteRepository.countDistinctVoters(MEETING_ID)).thenReturn(0L);
        when(dateVoteRepository.aggregateTopDates(any(), any(), any())).thenReturn(List.of());

        // when
        DateVoteResultResponse response = voteService.getMeetingScheduleVoteResult(USER_ID, MEETING_ID);

        // then
        assertThat(response.isVoteSkipped()).isFalse();
        assertThat(response.totalVoterCnt()).isEqualTo(0);
        assertThat(response.candidates()).isEmpty();
    }

    /**
     * Fixtures
     */

    private void assertErrorCode(ThrowingCallable callable, CustomErrorCode expected) {
        assertThatThrownBy(callable)
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getCustomErrorCode())
                .isEqualTo(expected);
    }

    private void stubMeetingAndParticipant(Meeting meeting) {
        stubMeetingAndParticipant(meeting, MY_PARTICIPANT_ID);
    }

    private void stubMeetingAndParticipant(Meeting meeting, Long participantId) {
        Participant participant = mock(Participant.class);
        lenient().when(participant.getId()).thenReturn(participantId);
        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.of(meeting));
        when(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(MEETING_ID, USER_ID))
                .thenReturn(Optional.of(participant));
    }
}

package com.moive.MoiveBE.domain.vote.service;

import com.moive.MoiveBE.domain.meeting.entity.Meeting;
import com.moive.MoiveBE.domain.meeting.entity.MeetingStatus;
import com.moive.MoiveBE.domain.meeting.entity.Participant;
import com.moive.MoiveBE.domain.meeting.repository.DateVoteRepository;
import com.moive.MoiveBE.domain.meeting.repository.MeetingRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantRepository;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendationRun;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendationStatus;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendationRunRepository;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendedPlaceRepository;
import com.moive.MoiveBE.domain.vote.dto.DateVoteSummary;
import com.moive.MoiveBE.domain.vote.dto.DateVoteResultResponse;
import com.moive.MoiveBE.domain.vote.dto.PlaceVoteRequest;
import com.moive.MoiveBE.domain.vote.entity.PlaceVote;
import com.moive.MoiveBE.domain.vote.repository.PlaceVoteRepository;
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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.moive.MoiveBE.global.exception.CustomErrorCode.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoteServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long MEETING_ID = 10L;
    private static final Long MY_PARTICIPANT_ID = 100L;

    private static final Long RECOMMENDATION_RUN_ID = 1000L;

    @Mock private MeetingRepository meetingRepository;
    @Mock private ParticipantRepository participantRepository;
    @Mock private DateVoteRepository dateVoteRepository;
    @Mock private PlaceVoteRepository placeVoteRepository;
    @Mock private RecommendationRunRepository recommendationRunRepository;
    @Mock private RecommendedPlaceRepository recommendedPlaceRepository;

    @InjectMocks private VoteService voteService;

    /**
     * [일정 투표 현황 조회] 테스트
     */

    @Test
    void 일정_투표_현황_조회시_존재하지_않는_모임이면_MEETING_NOT_FOUND() {
        // given
        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.empty());

        // when & then
        assertErrorCode(() -> voteService.getMeetingScheduleVoteResult(USER_ID, MEETING_ID), MEETING_NOT_FOUND);
        verifyNoInteractions(participantRepository, dateVoteRepository);
    }

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
     * [장소 투표] 테스트
     */

    @Test
    void 이미_장소가_확정된_모임이면_PLACE_VOTE_CLOSED() {
        // given
        Meeting meeting = meetingWithStatus(MeetingStatus.CONFIRMED);
        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.of(meeting));

        // when & then
        assertErrorCode(
                () -> voteService.createPlaceVote(USER_ID, MEETING_ID, placeVoteRequest(101L)),
                PLACE_VOTE_CLOSED
        );
        verifyNoInteractions(participantRepository, placeVoteRepository,
                recommendationRunRepository, recommendedPlaceRepository);
    }

    @Test
    void 조건_입력_중인_모임이면_PLACE_VOTE_NOT_STARTED() {
        // given
        Meeting meeting = meetingWithStatus(MeetingStatus.CONDITION_INPUT);
        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.of(meeting));

        // when & then
        assertErrorCode(
                () -> voteService.createPlaceVote(USER_ID, MEETING_ID, placeVoteRequest(101L)),
                PLACE_VOTE_NOT_STARTED
        );
        verifyNoInteractions(participantRepository, placeVoteRepository,
                recommendationRunRepository, recommendedPlaceRepository);
    }

    @Test
    void 장소_추천이_실행되지_않았다면_PLACE_VOTE_NOT_STARTED() {
        // given
        Meeting meeting = meetingWithStatus(MeetingStatus.VOTING);
        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.of(meeting));
        when(recommendationRunRepository.findTopByMeetingIdAndStatusOrderByCreatedAtDesc(MEETING_ID, RecommendationStatus.COMPLETED))
                .thenReturn(Optional.empty());

        // when & then
        assertErrorCode(
                () -> voteService.createPlaceVote(USER_ID, MEETING_ID, placeVoteRequest(101L)),
                PLACE_VOTE_NOT_STARTED
        );
        verifyNoInteractions(participantRepository, placeVoteRepository);
    }

    @Test
    void 장소_추천은_실행되었지만_추천_장소가_없으면_PLACE_VOTE_NOT_STARTED() {
        // given
        Meeting meeting = meetingWithStatus(MeetingStatus.VOTING);
        stubMeetingAndRecommendation(meeting);
        stubParticipant();
        when(placeVoteRepository.existsByMeetingIdAndParticipantId(MEETING_ID, MY_PARTICIPANT_ID)).thenReturn(false);

        // when & then
        assertErrorCode(
                () -> voteService.createPlaceVote(USER_ID, MEETING_ID, placeVoteRequest(101L)),
                PLACE_VOTE_NOT_STARTED
        );
        verify(placeVoteRepository, never()).saveAll(anyList());
    }

    @Test
    void 유저가_모임_참여자가_아니면_PLACE_VOTE_ACCESS_DENIED() {
        // given
        Meeting meeting = meetingWithStatus(MeetingStatus.VOTING);
        stubMeetingAndRecommendation(meeting, 101L, 102L);
        when(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(MEETING_ID, USER_ID))
                .thenReturn(Optional.empty());

        // when & then
        assertErrorCode(
                () -> voteService.createPlaceVote(USER_ID, MEETING_ID, placeVoteRequest(101L)),
                PLACE_VOTE_ACCESS_DENIED
        );
        verifyNoInteractions(placeVoteRepository);
    }

    @Test
    void 이미_투표한_참여자면_PLACE_VOTE_ALREADY_DONE() {
        // given
        Meeting meeting = meetingWithStatus(MeetingStatus.VOTING);
        stubMeetingAndRecommendation(meeting, 101L, 102L);
        stubParticipant();
        when(placeVoteRepository.existsByMeetingIdAndParticipantId(MEETING_ID, MY_PARTICIPANT_ID)).thenReturn(true);

        // when & then
        assertErrorCode(
                () -> voteService.createPlaceVote(USER_ID, MEETING_ID, placeVoteRequest(101L)),
                PLACE_VOTE_ALREADY_DONE
        );
        verify(placeVoteRepository, never()).saveAll(anyList());
    }

    @Test
    void 이_모임의_추천_장소가_아닌_id가_포함되면_PLACE_VOTE_INVALID_PLACE() {
        // given
        Meeting meeting = meetingWithStatus(MeetingStatus.VOTING);
        stubMeetingAndRecommendation(meeting, 101L, 102L);
        stubParticipant();
        when(placeVoteRepository.existsByMeetingIdAndParticipantId(MEETING_ID, MY_PARTICIPANT_ID)).thenReturn(false);

        // when & then: 999L은 이 모임의 추천 장소 목록(101L, 102L)에 없음
        assertErrorCode(
                () -> voteService.createPlaceVote(USER_ID, MEETING_ID, placeVoteRequest(101L, 999L)),
                PLACE_VOTE_INVALID_PLACE
        );
        verify(placeVoteRepository, never()).saveAll(anyList());
    }

    @Test
    void 장소_투표_요청에_null이_섞여있어도_NPE없이_PLACE_VOTE_INVALID_PLACE로_처리된다() {
        // given
        Meeting meeting = meetingWithStatus(MeetingStatus.VOTING);
        stubMeetingAndRecommendation(meeting, 101L, 102L);
        stubParticipant();
        when(placeVoteRepository.existsByMeetingIdAndParticipantId(MEETING_ID, MY_PARTICIPANT_ID)).thenReturn(false);

        PlaceVoteRequest request = new PlaceVoteRequest(Arrays.asList(101L, null));

        // when & then
        assertErrorCode(
                () -> voteService.createPlaceVote(USER_ID, MEETING_ID, request),
                PLACE_VOTE_INVALID_PLACE
        );
        verify(placeVoteRepository, never()).saveAll(anyList());
    }

    @Test
    void 정상_투표시_선택한_장소는_전부_저장되고_중복되는_id가_있으면_한번만_저장된다() {
        // given
        Meeting meeting = meetingWithStatus(MeetingStatus.VOTING);
        lenient().when(meeting.getParticipantCnt()).thenReturn(5);
        stubMeetingAndRecommendation(meeting, 101L, 102L, 103L);
        stubParticipant();
        when(placeVoteRepository.existsByMeetingIdAndParticipantId(MEETING_ID, MY_PARTICIPANT_ID)).thenReturn(false);
        when(placeVoteRepository.countDistinctVoters(MEETING_ID)).thenReturn(2L);

        // when: 101L 중복 선택
        voteService.createPlaceVote(USER_ID, MEETING_ID, placeVoteRequest(101L, 102L, 101L));

        // then
        ArgumentCaptor<List<PlaceVote>> captor = ArgumentCaptor.forClass(List.class);
        verify(placeVoteRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue()).extracting(PlaceVote::getRecommendedPlaceId)
                .containsExactlyInAnyOrder(101L, 102L);
        assertThat(captor.getValue()).allSatisfy(vote -> {
            assertThat(vote.getMeetingId()).isEqualTo(MEETING_ID);
            assertThat(vote.getParticipantId()).isEqualTo(MY_PARTICIPANT_ID);
        });
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

    private PlaceVoteRequest placeVoteRequest(Long... recommendedPlaceIds) {
        return new PlaceVoteRequest(List.of(recommendedPlaceIds));
    }

    private Meeting meetingWithStatus(MeetingStatus status) {
        Meeting meeting = mock(Meeting.class);
        lenient().when(meeting.getStatus()).thenReturn(status);
        return meeting;
    }

    private void stubMeetingAndRecommendation(Meeting meeting, Long... validRecommendedPlaceIds) {
        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.of(meeting));

        RecommendationRun run = mock(RecommendationRun.class);
        lenient().when(run.getId()).thenReturn(RECOMMENDATION_RUN_ID);
        when(recommendationRunRepository.findTopByMeetingIdAndStatusOrderByCreatedAtDesc(MEETING_ID, RecommendationStatus.COMPLETED))
                .thenReturn(Optional.of(run));
        lenient().when(recommendedPlaceRepository.findIdsByRecommendationRunId(RECOMMENDATION_RUN_ID))
                .thenReturn(List.of(validRecommendedPlaceIds));
    }

    private void stubParticipant() {
        Participant participant = mock(Participant.class);
        lenient().when(participant.getId()).thenReturn(MY_PARTICIPANT_ID);
        when(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(MEETING_ID, USER_ID))
                .thenReturn(Optional.of(participant));
    }
}

package com.moive.MoiveBE.domain.vote.service;

import com.moive.MoiveBE.domain.meeting.entity.Meeting;
import com.moive.MoiveBE.domain.meeting.entity.MeetingStatus;
import com.moive.MoiveBE.domain.meeting.repository.MeetingRepository;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendationRun;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendationStatus;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendationRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceVoteCloseServiceTest {

    private static final int DEADLINE_DAYS = 3;

    @Mock private MeetingRepository meetingRepository;
    @Mock private RecommendationRunRepository recommendationRunRepository;
    @Mock private VoteService voteService;

    @InjectMocks private PlaceVoteCloseService placeVoteCloseService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(placeVoteCloseService, "placeVoteDeadlineDays", DEADLINE_DAYS);
    }

    @Test
    void VOTING_상태인_모임이_없으면_아무것도_하지_않는다() {
        when(meetingRepository.findAllByStatus(MeetingStatus.VOTING)).thenReturn(List.of());

        placeVoteCloseService.finalizeExpiredPlaceVotes();

        verifyNoInteractions(recommendationRunRepository, voteService);
    }

    @Test
    void 장소_추천이_완료되지_않은_모임은_대상에서_제외한다() {
        // given: VOTING 상태지만 완료된 추천 실행이 아직 없음 (장소 투표 전)
        stubVotingMeetings(10L);
        when(recommendationRunRepository.findAllByMeetingIdInAndStatus(List.of(10L), RecommendationStatus.COMPLETED))
                .thenReturn(List.of());

        placeVoteCloseService.finalizeExpiredPlaceVotes();

        verifyNoInteractions(voteService);
    }

    @Test
    void 마감_기한이_지나지_않은_모임은_대상에서_제외한다() {
        stubVotingMeetings(10L);
        stubCompletedRuns(completedRunAt(10L, LocalDateTime.now().minusDays(DEADLINE_DAYS - 1)));

        placeVoteCloseService.finalizeExpiredPlaceVotes();

        verifyNoInteractions(voteService);
    }

    @Test
    void 마감_기한이_지난_모임만_모임별로_마감_처리를_요청한다() {
        // given: A=기한 안 지남, B&C=기한 지남
        stubVotingMeetings(21L, 22L, 23L);
        stubCompletedRuns(
                completedRunAt(21L, LocalDateTime.now().minusDays(DEADLINE_DAYS - 1)),
                completedRunAt(22L, LocalDateTime.now().minusDays(DEADLINE_DAYS + 2)),
                completedRunAt(23L, LocalDateTime.now().minusDays(DEADLINE_DAYS + 2))
        );

        placeVoteCloseService.finalizeExpiredPlaceVotes();

        verify(voteService, never()).finalizeExpiredPlaceVote(21L);
        verify(voteService).finalizeExpiredPlaceVote(22L);
        verify(voteService).finalizeExpiredPlaceVote(23L);
    }

    @Test
    void 한_모임의_마감_처리가_실패해도_나머지_모임은_계속_처리한다() {
        // given: B 처리 중 예외 발생
        stubVotingMeetings(21L, 22L, 23L);
        LocalDateTime expired = LocalDateTime.now().minusDays(DEADLINE_DAYS + 2);
        stubCompletedRuns(completedRunAt(21L, expired), completedRunAt(22L, expired), completedRunAt(23L, expired));
        when(voteService.finalizeExpiredPlaceVote(21L)).thenReturn(true);
        when(voteService.finalizeExpiredPlaceVote(22L)).thenThrow(new IllegalStateException("장소 정보 누락"));
        when(voteService.finalizeExpiredPlaceVote(23L)).thenReturn(true);

        // when: 예외가 밖으로 전파되지 않음
        placeVoteCloseService.finalizeExpiredPlaceVotes();

        // then: 실패한 B 이후의 C까지 처리
        verify(voteService).finalizeExpiredPlaceVote(21L);
        verify(voteService).finalizeExpiredPlaceVote(22L);
        verify(voteService).finalizeExpiredPlaceVote(23L);
    }

    @Test
    void 추천_실행이_여러_번_완료된_모임은_가장_늦게_완료된_시각을_장소_투표_시작_시점으로_본다() {
        // given: 이전 추천은 기한이 지났지만, 가장 최근 추천은 기한 전
        stubVotingMeetings(10L);
        stubCompletedRuns(
                completedRunAt(10L, LocalDateTime.now().minusDays(DEADLINE_DAYS + 5)),
                completedRunAt(10L, LocalDateTime.now().minusDays(DEADLINE_DAYS - 1))
        );

        placeVoteCloseService.finalizeExpiredPlaceVotes();

        verify(voteService, never()).finalizeExpiredPlaceVote(anyLong());
    }

    private void stubVotingMeetings(Long... meetingIds) {
        List<Meeting> meetings = Arrays.stream(meetingIds)
                .map(id -> {
                    Meeting meeting = mock(Meeting.class);
                    lenient().when(meeting.getId()).thenReturn(id);
                    return meeting;
                })
                .toList();
        when(meetingRepository.findAllByStatus(MeetingStatus.VOTING)).thenReturn(meetings);
    }

    private void stubCompletedRuns(RecommendationRun... runs) {
        when(recommendationRunRepository.findAllByMeetingIdInAndStatus(anyList(), eq(RecommendationStatus.COMPLETED)))
                .thenReturn(List.of(runs));
    }

    private RecommendationRun completedRunAt(Long meetingId, LocalDateTime updatedAt) {
        RecommendationRun run = mock(RecommendationRun.class);
        lenient().when(run.getMeetingId()).thenReturn(meetingId);
        lenient().when(run.getUpdatedAt()).thenReturn(updatedAt);
        return run;
    }
}

package com.moive.MoiveBE.global.scheduler;

import com.moive.MoiveBE.domain.meeting.service.MeetingService;
import com.moive.MoiveBE.domain.vote.service.PlaceVoteCloseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeetingLifecycleSchedulerTest {

    @Mock private PlaceVoteCloseService placeVoteCloseService;
    @Mock private MeetingService meetingService;

    @InjectMocks private MeetingLifecycleScheduler scheduler;

    @BeforeEach
    void setUp() {
        // 재시도 대기 시간 제거
        ReflectionTestUtils.setField(scheduler, "completeRetryBackoffMillis", new long[]{0L, 0L});
    }

    @Test
    void 자정_배치는_장소_투표_마감_후_모임_종료_순서로_실행한다() {
        scheduler.run();

        InOrder inOrder = inOrder(placeVoteCloseService, meetingService);
        inOrder.verify(placeVoteCloseService).finalizeExpiredPlaceVotes();
        inOrder.verify(meetingService).completeElapsedMeetings();
    }

    @Test
    void 장소_투표_마감_단계가_실패해도_모임_종료_단계는_실행하고_예외를_전파하지_않는다() {
        // given
        doThrow(new DataAccessResourceFailureException("DB 연결 실패"))
                .when(placeVoteCloseService).finalizeExpiredPlaceVotes();

        // when & then
        assertThatCode(() -> scheduler.run()).doesNotThrowAnyException();
        verify(meetingService).completeElapsedMeetings();
    }

    @Test
    void 모임_종료_단계가_일시적으로_실패하면_재시도해_처리한다() {
        // given: 두 번 실패 후 성공
        when(meetingService.completeElapsedMeetings())
                .thenThrow(new DataAccessResourceFailureException("DB 일시 오류"))
                .thenThrow(new DataAccessResourceFailureException("DB 일시 오류"))
                .thenReturn(2);

        // when
        scheduler.run();

        // then
        verify(meetingService, times(3)).completeElapsedMeetings();
    }

    @Test
    void 모임_종료_단계가_계속_실패하면_최대_3회까지만_시도하고_예외를_전파하지_않는다() {
        // given
        when(meetingService.completeElapsedMeetings())
                .thenThrow(new DataAccessResourceFailureException("DB 연결 실패"));

        // when & then
        assertThatCode(() -> scheduler.run()).doesNotThrowAnyException();
        verify(meetingService, times(3)).completeElapsedMeetings();
    }

    @Test
    void 서버_시작_시에는_모임_종료_단계만_실행한다() {
        scheduler.completeElapsedMeetingsOnStartup();

        verify(meetingService).completeElapsedMeetings();
        verifyNoInteractions(placeVoteCloseService);
    }
}

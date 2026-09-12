package com.moive.MoiveBE.global.scheduler;

import com.moive.MoiveBE.domain.meeting.service.MeetingService;
import com.moive.MoiveBE.domain.vote.service.VoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 모임/투표 상태 처리 배치
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MeetingLifecycleScheduler {

    private final VoteService voteService;
    private final MeetingService meetingService;

    @Scheduled(cron = "${meeting.daily-batch-cron}")
    public void run() {
        LocalDateTime startDateTime = LocalDateTime.now();
        long startMillis = System.currentTimeMillis();
        log.info("[모임/투표 배치] 시작 - 시작 시각: {}", startDateTime);

        try {
            voteService.finalizeExpiredPlaceVotes();
            meetingService.completeElapsedMeetings();
        } catch (Exception e) {
            log.error("[모임/투표 배치] 실행 중 오류 발생", e);
            throw e;
        } finally {
            LocalDateTime endTime = LocalDateTime.now();
            long duration = System.currentTimeMillis() - startMillis;
            log.info("[모임/투표 배치] 종료 - 종료 시각: {}, 소요 시간: {}ms", endTime, duration);
        }
    }
}

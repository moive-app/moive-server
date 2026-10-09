package com.moive.MoiveBE.global.scheduler;

import com.moive.MoiveBE.domain.meeting.service.MeetingService;
import com.moive.MoiveBE.domain.vote.service.PlaceVoteCloseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 모임/투표 상태 처리 배치
 * - 매일 자정: 장소 투표 마감(VOTING -> CONFIRMED) → 모임 종료(CONFIRMED -> COMPLETED)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MeetingLifecycleScheduler {

    // 모임 종료 처리 최대 시도 횟수, 재시도 전 대기 시간(ms)
    private static final int COMPLETE_MAX_ATTEMPTS = 3;
    private long[] completeRetryBackoffMillis = {1_000L, 3_000L};

    private final PlaceVoteCloseService placeVoteCloseService;
    private final MeetingService meetingService;

    @Scheduled(cron = "${meeting.daily-batch-cron}")
    public void run() {
        LocalDateTime startDateTime = LocalDateTime.now();
        long startMillis = System.currentTimeMillis();
        log.info("[모임/투표 배치] 시작 - 시작 시각: {}", startDateTime);

        try {
            // 장소 투표 마감 처리
            closeExpiredPlaceVotes();
            // 모임 종료 처리
            completeElapsedMeetings();
        } finally {
            LocalDateTime endTime = LocalDateTime.now();
            long duration = System.currentTimeMillis() - startMillis;
            log.info("[모임/투표 배치] 종료 - 종료 시각: {}, 소요 시간: {}ms", endTime, duration);
        }
    }

    // 장소 투표 마감 처리
    private void closeExpiredPlaceVotes() {
        try {
            placeVoteCloseService.finalizeExpiredPlaceVotes();
        } catch (Exception e) {
            log.error("[모임/투표 배치] 장소 투표 마감 처리 실패", e);
        }
    }

    // 모임 종료 처리
    private void completeElapsedMeetings() {
        for (int attempt = 1; attempt <= COMPLETE_MAX_ATTEMPTS; attempt++) {
            try {
                meetingService.completeElapsedMeetings();
                return;
            } catch (Exception e) {
                if (attempt == COMPLETE_MAX_ATTEMPTS) {
                    log.error("[모임/투표 배치] 모임 종료 처리 최종 실패 ({}/{}회 시도)",
                            attempt, COMPLETE_MAX_ATTEMPTS, e);
                    return;
                }
                log.warn("[모임/투표 배치] 모임 종료 처리 실패 ({}/{}회 시도, 재시도 예정)", attempt, COMPLETE_MAX_ATTEMPTS, e);
                if (!sleepBeforeRetry(completeRetryBackoffMillis[attempt - 1])) {
                    return;
                }
            }
        }
    }

    // 서버 시작 시 모임 종료 처리 실행
    @EventListener(ApplicationReadyEvent.class)
    public void completeElapsedMeetingsOnStartup() {
        log.info("[모임/투표 배치] 서버 시작 - 누락된 모임 종료 처리 확인");
        completeElapsedMeetings();
    }

    private boolean sleepBeforeRetry(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[모임/투표 배치] 모임 종료 처리 재시도 대기 중 중단됨");
            return false;
        }
    }
}

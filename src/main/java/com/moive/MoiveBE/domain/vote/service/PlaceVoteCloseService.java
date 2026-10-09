package com.moive.MoiveBE.domain.vote.service;

import com.moive.MoiveBE.domain.meeting.entity.Meeting;
import com.moive.MoiveBE.domain.meeting.entity.MeetingStatus;
import com.moive.MoiveBE.domain.meeting.repository.MeetingRepository;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendationRun;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendationStatus;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendationRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 장소 투표 마감 배치
 * - 장소 투표 마감 기한이 지났는데도 전원이 투표를 마치지 못한 모임 확정
 * - 매일 MeetingLifecycleScheduler에서 호출됨
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlaceVoteCloseService {

    private final MeetingRepository meetingRepository;
    private final RecommendationRunRepository recommendationRunRepository;
    private final VoteService voteService;

    @Value("${place-vote.deadline-days}")
    private int placeVoteDeadlineDays;

    // 트랜잭션 처리 x: 모임별 트랜잭션(VoteService.finalizeExpiredPlaceVote)으로 실패를 격리하기 위함
    public void finalizeExpiredPlaceVotes() {
        long startMillis = System.currentTimeMillis();
        LocalDateTime deadline = LocalDateTime.now().minusDays(placeVoteDeadlineDays);

        List<Long> targetMeetingIds = findExpiredVotingMeetingIds(deadline);
        log.info("[장소 투표 마감 배치] 시작 - 대상 {}건 (마감 기준 시각={}, 대상 meetingId={})",
                targetMeetingIds.size(), deadline, targetMeetingIds);

        int confirmedCnt = 0;
        int skippedCnt = 0;
        List<Long> failedMeetingIds = new ArrayList<>();

        for (Long meetingId : targetMeetingIds) {
            try {
                if (voteService.finalizeExpiredPlaceVote(meetingId)) {
                    confirmedCnt++;
                } else {
                    skippedCnt++;
                }
            } catch (Exception e) {
                failedMeetingIds.add(meetingId);
                log.error("[장소 투표 마감 배치] 모임 확정 실패 - 다음 실행에서 재시도 예정 (meetingId={})", meetingId, e);
            }
        }

        long elapsedMillis = System.currentTimeMillis() - startMillis;
        if (failedMeetingIds.isEmpty()) {
            log.info("[장소 투표 마감 배치] 완료 - 대상 {}건, 확정 {}건, 건너뜀 {}건, 실패 0건, 소요 {}ms",
                    targetMeetingIds.size(), confirmedCnt, skippedCnt, elapsedMillis);
        } else {
            log.warn("[장소 투표 마감 배치] 완료(실패 있음) - 대상 {}건, 확정 {}건, 건너뜀 {}건, 실패 {}건 (실패 meetingId={}), 소요 {}ms",
                    targetMeetingIds.size(), confirmedCnt, skippedCnt, failedMeetingIds.size(), failedMeetingIds, elapsedMillis);
        }
    }

    // 마감 기한이 지난 장소 투표 진행 중(VOTING) 모임 ID 목록
    private List<Long> findExpiredVotingMeetingIds(LocalDateTime deadline) {
        List<Long> votingMeetingIds = meetingRepository.findAllByStatus(MeetingStatus.VOTING).stream()
                .map(Meeting::getId)
                .toList();
        if (votingMeetingIds.isEmpty()) {
            return List.of();
        }

        Map<Long, LocalDateTime> placeVoteStartedAtByMeetingId = recommendationRunRepository
                .findAllByMeetingIdInAndStatus(votingMeetingIds, RecommendationStatus.COMPLETED)
                .stream()
                .collect(Collectors.toMap(
                        RecommendationRun::getMeetingId,
                        RecommendationRun::getUpdatedAt,
                        (earlier, later) -> earlier.isAfter(later) ? earlier : later
                ));

        return votingMeetingIds.stream()
                .filter(meetingId -> {
                    LocalDateTime placeVoteStartedAt = placeVoteStartedAtByMeetingId.get(meetingId);
                    return placeVoteStartedAt != null && !placeVoteStartedAt.isAfter(deadline);
                })
                .toList();
    }
}

package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.meeting.entity.Participant;
import com.moive.MoiveBE.domain.meeting.repository.MeetingRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantRepository;
import com.moive.MoiveBE.domain.notification.entity.NotificationType;
import com.moive.MoiveBE.domain.notification.service.NotificationService;
import com.moive.MoiveBE.domain.recommendation.dto.AreaCandidate;
import com.moive.MoiveBE.domain.recommendation.dto.AreaScoreResult;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendationRun;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendedArea;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendationRunRepository;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendedAreaRepository;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AreaRecommendationSaveService {

    private final RecommendationRunRepository recommendationRunRepository;
    private final RecommendedAreaRepository recommendedAreaRepository;
    private final MeetingRepository meetingRepository;
    private final ParticipantRepository participantRepository;
    private final NotificationService notificationService;

    @Transactional
    public RecommendationRun save(
            Long meetingId,
            List<AreaCandidate> candidates,
            List<AreaScoreResult> top3
    ) {

        RecommendationRun run =
                recommendationRunRepository.save(
                        RecommendationRun.create(meetingId)
                );

        List<RecommendedArea> recommendedAreas =
                top3.stream()
                        .map(scoreResult -> {

                            AreaCandidate candidate =
                                    candidates.get(
                                            scoreResult.destinationIndex()
                                    );

                            return RecommendedArea.create(
                                    run.getId(),
                                    candidate.name()
                            );
                        })
                        .toList();

        recommendedAreaRepository.saveAll(
                recommendedAreas
        );

        run.complete();

        // NOTI-002: 장소 추천 완료 알림 (전체 참여자)
        String meetingName = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new CustomException(CustomErrorCode.MEETING_NOT_FOUND))
                .getName();
        List<Participant> participants = participantRepository
                .findAllByMeetingIdAndLeftAtIsNullOrderByJoinedAtAsc(meetingId);
        for (Participant p : participants) {
            notificationService.sendNotification(
                    p.getUserId(), meetingId, NotificationType.PLACE_RECOMMEND,
                    "'" + meetingName + "'의 장소 추천이 완료됐어요. 지금 투표하세요!");
        }

        return run;
    }
}
package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.meeting.entity.Participant;
import com.moive.MoiveBE.domain.meeting.entity.ParticipantPreference;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantPreferenceRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantRepository;
import com.moive.MoiveBE.domain.recommendation.dto.AreaCandidate;
import com.moive.MoiveBE.domain.recommendation.dto.AreaCenter;
import com.moive.MoiveBE.domain.recommendation.dto.AreaRouteResult;
import com.moive.MoiveBE.domain.recommendation.dto.AreaScoreResult;
import com.moive.MoiveBE.domain.recommendation.dto.GoogleRouteMatrixResponse;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AreaRecommendationService {

    private final ParticipantRepository participantRepository;
    private final ParticipantPreferenceRepository participantPreferenceRepository;

    private final AreaCenterService areaCenterService;
    private final LegalDongCandidateService legalDongCandidateService;
    private final AreaRouteService areaRouteService;
    private final RouteMatrixService routeMatrixService;
    private final AreaScoreService areaScoreService;
    private final AreaRecommendationSaveService areaRecommendationSaveService;

    private List<ParticipantPreference> getPreferences(
            Long meetingId
    ) {

        List<Participant> participants =
                participantRepository.findAllByMeetingIdAndLeftAtIsNull(
                        meetingId
                );

        List<Long> participantIds =
                participants.stream()
                        .map(Participant::getId)
                        .toList();

        return participantPreferenceRepository.findAllByParticipantIdIn(
                participantIds
        );
    }

    @Async("recommendationExecutor")
    public void recommend(Long meetingId) {

        try {
            log.info(
                    "[1] 비동기 지역 추천 시작 - meetingId={}",
                    meetingId
            );

            List<ParticipantPreference> preferences =
                    getPreferences(meetingId);

            log.info(
                    "[2] preferences 조회 완료 - count={}",
                    preferences.size()
            );

            AreaCenter center =
                    areaCenterService.calculate(preferences);

            log.info(
                    "[3] center 계산 완료 - center={}",
                    center
            );

            List<AreaCandidate> candidates =
                    legalDongCandidateService.generate(center);

            log.info(
                    "[4] 지역 후보 생성 완료 - count={}, candidates={}",
                    candidates.size(),
                    candidates
            );

            List<GoogleRouteMatrixResponse> responses =
                    areaRouteService.calculate(
                            preferences,
                            candidates
                    );

            log.info(
                    "[5] 경로 API 계산 완료 - response count={}",
                    responses.size()
            );

            List<AreaRouteResult> routeResults =
                    routeMatrixService.calculateAreaRouteResults(
                            candidates,
                            responses,
                            preferences.size()
                    );

            log.info(
                    "[6] routeResults 계산 완료 - count={}",
                    routeResults.size()
            );

            if (routeResults.size() < 3) {
                throw new CustomException(
                        CustomErrorCode.INSUFFICIENT_AREA_CANDIDATES
                );
            }

            List<AreaScoreResult> scoreResults =
                    areaScoreService.calculateScores(
                            routeResults
                    );

            log.info(
                    "[7] 지역 점수 계산 완료 - count={}",
                    scoreResults.size()
            );

            List<AreaScoreResult> top3 =
                    areaScoreService.selectTop3(
                            scoreResults
                    );

            log.info(
                    "[8] TOP3 선정 완료 - count={}",
                    top3.size()
            );

            areaRecommendationSaveService.save(
                    meetingId,
                    candidates,
                    top3
            );

            log.info(
                    "[9] 지역 추천 저장 완료 - meetingId={}",
                    meetingId
            );

        } catch (Exception e) {

            log.error(
                    "지역 추천 비동기 처리 실패 - meetingId={}",
                    meetingId,
                    e
            );
        }
    }
}
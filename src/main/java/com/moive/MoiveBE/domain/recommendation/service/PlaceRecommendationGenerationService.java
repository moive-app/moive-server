package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.meeting.entity.Participant;
import com.moive.MoiveBE.domain.meeting.entity.ParticipantPreference;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantPreferenceRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantRepository;
import com.moive.MoiveBE.domain.recommendation.dto.GoogleRouteMatrixResponse;
import com.moive.MoiveBE.domain.recommendation.dto.ParticipantRecommendationCondition;
import com.moive.MoiveBE.domain.recommendation.dto.PlaceCandidate;
import com.moive.MoiveBE.domain.recommendation.dto.PlaceMatchResult;
import com.moive.MoiveBE.domain.recommendation.dto.PlaceRankingResult;
import com.moive.MoiveBE.domain.recommendation.dto.PlaceRouteResult;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendedArea;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendedPlace;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendationRun;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendedAreaRepository;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendedPlaceRepository;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendationRunRepository;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlaceRecommendationGenerationService {

    private final RecommendedAreaRepository recommendedAreaRepository;
    private final RecommendationRunRepository recommendationRunRepository;
    private final RecommendedPlaceRepository recommendedPlaceRepository;

    private final ParticipantRepository participantRepository;
    private final ParticipantPreferenceRepository participantPreferenceRepository;

    private final ParticipantRecommendationConditionService
            participantRecommendationConditionService;

    private final PlacePreferenceAllocationService
            placePreferenceAllocationService;

    private final PlaceCandidateGenerationService
            placeCandidateGenerationService;

    private final PlaceRouteService placeRouteService;
    private final RouteMatrixService routeMatrixService;
    private final PlaceMatchService placeMatchService;
    private final PlaceRankingService placeRankingService;

    @Transactional
    public List<RecommendedPlace> recommend(Long recommendedAreaId) {

        // 1. 선택된 추천 지역 조회
        RecommendedArea recommendedArea =
                recommendedAreaRepository.findById(recommendedAreaId)
                        .orElseThrow(() ->
                                new CustomException(
                                        CustomErrorCode.RECOMMENDED_AREA_NOT_FOUND
                                )
                        );

        // 2. 추천 실행 정보에서 meetingId 조회
        RecommendationRun recommendationRun =
                recommendationRunRepository.findById(
                                recommendedArea.getRecommendationRunId()
                        )
                        .orElseThrow(() ->
                                new CustomException(
                                        CustomErrorCode.RECOMMENDATION_RESULT_NOT_FOUND
                                )
                        );

        Long meetingId = recommendationRun.getMeetingId();

        // 3. 현재 모임 참가자 조회
        List<Participant> participants =
                participantRepository
                        .findAllByMeetingIdAndLeftAtIsNull(meetingId);

        if (participants.isEmpty()) {
            throw new IllegalStateException(
                    "추천을 계산할 참가자가 존재하지 않습니다."
            );
        }

        List<Long> participantIds = participants.stream()
                .map(Participant::getId)
                .toList();

        // 4. 참가자 선호조건 조회
        List<ParticipantPreference> preferences =
                participantPreferenceRepository
                        .findAllByParticipantIdIn(participantIds);

        Map<Long, ParticipantPreference> preferenceMap =
                preferences.stream()
                        .collect(Collectors.toMap(
                                ParticipantPreference::getParticipantId,
                                Function.identity()
                        ));

        /*
         * Participant 순서에 맞춰 다시 정렬한다.
         *
         * 이 순서가 이후
         * ParticipantRecommendationCondition.originIndex와
         * Google Routes originIndex의 기준이 된다.
         */
        List<ParticipantPreference> orderedPreferences =
                participants.stream()
                        .map(participant -> {
                            ParticipantPreference preference =
                                    preferenceMap.get(participant.getId());

                            if (preference == null) {
                                throw new IllegalStateException(
                                        "참가자의 선호조건이 존재하지 않습니다."
                                );
                            }

                            return preference;
                        })
                        .toList();

        // 5. 참가자별 추천 계산 조건 생성
        List<ParticipantRecommendationCondition> conditions =
                participantRecommendationConditionService
                        .createConditions(orderedPreferences);

        // 6. 활동별 장소 후보 개수 배분
        Map<String, Integer> allocations =
                placePreferenceAllocationService.allocate(conditions);

        if (allocations.isEmpty()) {
            throw new IllegalStateException(
                    "장소 후보를 생성할 선호 활동이 존재하지 않습니다."
            );
        }

        // 7. Google Places를 이용해 장소 후보 생성
        List<PlaceCandidate> candidates =
                placeCandidateGenerationService.generateCandidates(
                        recommendedArea.getAreaName(),
                        allocations
                );

        if (candidates.isEmpty()) {
            throw new IllegalStateException(
                    "추천 가능한 장소 후보가 존재하지 않습니다."
            );
        }

        // 8. 참가자 출발지 → 장소 후보 이동시간 조회
        List<GoogleRouteMatrixResponse> routeResponses =
                placeRouteService.calculate(
                        orderedPreferences,
                        candidates
                );

        int originCount = orderedPreferences.size();

        // 9. 장소별 평균/최대 이동시간 계산
        List<PlaceRouteResult> routeResults =
                routeMatrixService.calculateRouteResults(
                        candidates,
                        routeResponses,
                        originCount
                );

        // 10. 참가자별 선호 + 최대 이동시간 일치 수 계산
        List<PlaceMatchResult> matchResults =
                placeMatchService.calculateMatchCounts(
                        candidates,
                        conditions,
                        routeResponses
                );

        // 11. 선호 일치 수 → 평균 이동시간 → 최대 이동시간 순으로 TOP3
        List<PlaceRankingResult> topPlaces =
                placeRankingService.selectTopPlaces(
                        candidates,
                        routeResults,
                        matchResults
                );

        // 12. googlePlaceId 기준으로 원본 후보 조회용 Map 생성
        Map<String, PlaceCandidate> candidateMap =
                candidates.stream()
                        .collect(Collectors.toMap(
                                PlaceCandidate::googlePlaceId,
                                Function.identity()
                        ));

// 13. TOP3만 RecommendedPlace에 저장
        List<RecommendedPlace> recommendedPlaces =
                topPlaces.stream()
                        .map(result -> {
                            PlaceCandidate candidate =
                                    candidateMap.get(result.googlePlaceId());

                            if (candidate == null) {
                                throw new IllegalStateException(
                                        "추천 장소 후보 정보가 존재하지 않습니다."
                                );
                            }

                            return RecommendedPlace.create(
                                    recommendedAreaId,
                                    result.googlePlaceId(),
                                    candidate.preferenceType(),
                                    result.preferenceMatchCnt()
                            );
                        })
                        .toList();

        return recommendedPlaceRepository.saveAll(
                recommendedPlaces
        );
    }
}
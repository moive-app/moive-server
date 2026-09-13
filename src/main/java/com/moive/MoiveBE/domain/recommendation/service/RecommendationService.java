package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.meeting.entity.Participant;
import com.moive.MoiveBE.domain.meeting.entity.ParticipantPreference;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantPreferenceRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantRepository;
import com.moive.MoiveBE.domain.recommendation.client.GooglePlacesClient;
import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceDetailsResponse;
import com.moive.MoiveBE.domain.recommendation.dto.GoogleRouteMatrixResponse;
import com.moive.MoiveBE.domain.recommendation.dto.PlaceCandidate;
import com.moive.MoiveBE.domain.recommendation.dto.PlaceRouteResult;
import com.moive.MoiveBE.domain.recommendation.dto.RecommendedPlaceDetailResponse;
import com.moive.MoiveBE.domain.recommendation.dto.RecommendedPlaceListResponse;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendationRun;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendedArea;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendedPlace;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendationRunRepository;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendedAreaRepository;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendedPlaceRepository;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final RecommendedPlaceRepository recommendedPlaceRepository;
    private final GooglePlacesClient googlePlacesClient;
    private final RecommendedAreaRepository recommendedAreaRepository;
    private final RecommendationRunRepository recommendationRunRepository;
    private final PlaceRecommendationGenerationService placeRecommendationGenerationService;
    private final ParticipantRepository participantRepository;
    private final ParticipantPreferenceRepository participantPreferenceRepository;
    private final PlaceRouteService placeRouteService;
    private final RouteMatrixService routeMatrixService;

    @Transactional
    public RecommendedPlaceListResponse getRecommendedPlaces(
            Long meetingId,
            Long recommendedAreaId
    ) {
        validateRecommendedArea(meetingId, recommendedAreaId);

        /*
         * 아직 추천 장소가 생성되지 않았다면
         * 해당 추천 지역 기준으로 장소 추천 TOP3 생성
         */
        if (!recommendedPlaceRepository.existsByRecommendedAreaId(recommendedAreaId)) {
            placeRecommendationGenerationService.recommend(recommendedAreaId);
        }

        List<RecommendedPlace> recommendedPlaces =
                recommendedPlaceRepository.findAllByRecommendedAreaId(
                        recommendedAreaId
                );

        if (recommendedPlaces.isEmpty()) {
            return new RecommendedPlaceListResponse(
                    recommendedAreaId,
                    List.of()
            );
        }

        /*
         * 현재 모임의 활성 참가자 조회
         */
        List<Participant> participants =
                participantRepository.findAllByMeetingIdAndLeftAtIsNull(
                        meetingId
                );

        int participantCount = participants.size();

        /*
         * 참가자 출발 위치 조회
         */
        List<ParticipantPreference> orderedPreferences =
                getOrderedParticipantPreferences(participants);

        /*
         * 저장된 googlePlaceId로 장소 정보와 좌표 조회
         *
         * Google 장소 좌표는 DB에 저장하지 않고
         * 현재 요청에서만 사용
         */
        Map<String, GooglePlaceDetailsResponse> placeDetailsMap =
                new HashMap<>();

        List<PlaceCandidate> candidates =
                IntStream.range(0, recommendedPlaces.size())
                        .mapToObj(destinationIndex -> {

                            RecommendedPlace recommendedPlace =
                                    recommendedPlaces.get(destinationIndex);

                            GooglePlaceDetailsResponse details =
                                    googlePlacesClient.getPlaceSummaryDetails(
                                            recommendedPlace.getGooglePlaceId()
                                    );

                            if (details == null || details.location() == null) {
                                throw new CustomException(
                                        CustomErrorCode.PLACE_INFO_LOOKUP_FAILED
                                );
                            }

                            placeDetailsMap.put(
                                    recommendedPlace.getGooglePlaceId(),
                                    details
                            );

                            return new PlaceCandidate(
                                    recommendedPlace.getGooglePlaceId(),
                                    details.location().latitude(),
                                    details.location().longitude(),
                                    destinationIndex,
                                    recommendedPlace.getCategory()
                            );
                        })
                        .toList();

        /*
         * 참가자 출발 위치 → 추천 장소 이동시간 조회
         */
        List<GoogleRouteMatrixResponse> routeResponses =
                placeRouteService.calculate(
                        orderedPreferences,
                        candidates
                );

        /*
         * 장소별 평균 / 최대 이동시간 계산
         */
        List<PlaceRouteResult> routeResults =
                routeMatrixService.calculateRouteResults(
                        candidates,
                        routeResponses,
                        orderedPreferences.size()
                );

        Map<Integer, PlaceRouteResult> routeResultMap =
                routeResults.stream()
                        .collect(Collectors.toMap(
                                PlaceRouteResult::destinationIndex,
                                Function.identity()
                        ));

        /*
         * 최종 응답 생성
         */
        List<RecommendedPlaceListResponse.Place> places =
                IntStream.range(0, recommendedPlaces.size())
                        .mapToObj(destinationIndex -> {

                            RecommendedPlace recommendedPlace =
                                    recommendedPlaces.get(destinationIndex);

                            GooglePlaceDetailsResponse details =
                                    placeDetailsMap.get(
                                            recommendedPlace.getGooglePlaceId()
                                    );

                            PlaceRouteResult routeResult =
                                    routeResultMap.get(destinationIndex);

                            if (routeResult == null) {
                                throw new IllegalStateException(
                                        "장소 이동시간 계산 결과가 존재하지 않습니다."
                                );
                            }

                            return toPlaceResponse(
                                    recommendedPlace,
                                    details,
                                    participantCount,
                                    routeResult
                            );
                        })
                        .toList();

        return new RecommendedPlaceListResponse(
                recommendedAreaId,
                places
        );
    }

    public RecommendedPlaceDetailResponse getRecommendedPlaceDetail(
            Long meetingId,
            Long recommendedAreaId,
            Long recommendedPlaceId
    ) {
        /*
         * 해당 추천 지역이 요청한 모임에 속하는지 검증
         */
        validateRecommendedArea(
                meetingId,
                recommendedAreaId
        );

        /*
         * 추천 지역 조회
         */
        RecommendedArea recommendedArea =
                recommendedAreaRepository.findById(recommendedAreaId)
                        .orElseThrow(() ->
                                new CustomException(
                                        CustomErrorCode.RECOMMENDED_AREA_NOT_FOUND
                                )
                        );

        /*
         * 추천 장소 조회
         */
        RecommendedPlace recommendedPlace =
                recommendedPlaceRepository.findById(recommendedPlaceId)
                        .orElseThrow(() ->
                                new CustomException(
                                        CustomErrorCode.RECOMMENDED_PLACE_NOT_FOUND
                                )
                        );

        /*
         * 해당 추천 지역에 속한 장소인지 검증
         */
        if (!recommendedPlace.getRecommendedAreaId().equals(recommendedAreaId)) {
            throw new CustomException(
                    CustomErrorCode.RECOMMENDED_PLACE_NOT_FOUND
            );
        }

        /*
         * Google Place 상세 정보 조회
         */
        GooglePlaceDetailsResponse details =
                googlePlacesClient.getPlaceDetails(
                        recommendedPlace.getGooglePlaceId()
                );

        if (details == null || details.location() == null) {
            throw new CustomException(
                    CustomErrorCode.PLACE_INFO_LOOKUP_FAILED
            );
        }

        /*
         * 모임의 활성 참가자 조회
         */
        List<Participant> participants =
                participantRepository.findAllByMeetingIdAndLeftAtIsNull(
                        meetingId
                );

        if (participants.isEmpty()) {
            throw new IllegalStateException(
                    "이동시간을 계산할 참가자가 존재하지 않습니다."
            );
        }

        /*
         * 참가자 순서에 맞는 출발 위치 조회
         */
        List<ParticipantPreference> orderedPreferences =
                getOrderedParticipantPreferences(participants);

        /*
         * 상세 조회 장소 하나를 임시 PlaceCandidate로 생성
         *
         * 좌표는 DB에 저장하지 않고 현재 요청에서만 사용
         */
        PlaceCandidate candidate =
                new PlaceCandidate(
                        recommendedPlace.getGooglePlaceId(),
                        details.location().latitude(),
                        details.location().longitude(),
                        0,
                        recommendedPlace.getCategory()
                );

        List<PlaceCandidate> candidates =
                List.of(candidate);

        /*
         * 참가자 출발 위치 → 해당 장소 이동시간 조회
         */
        List<GoogleRouteMatrixResponse> routeResponses =
                placeRouteService.calculate(
                        orderedPreferences,
                        candidates
                );

        /*
         * 장소 평균 이동시간 계산
         */
        List<PlaceRouteResult> routeResults =
                routeMatrixService.calculateRouteResults(
                        candidates,
                        routeResponses,
                        orderedPreferences.size()
                );

        if (routeResults.isEmpty()) {
            throw new IllegalStateException(
                    "장소 이동시간 계산 결과가 존재하지 않습니다."
            );
        }

        PlaceRouteResult routeResult =
                routeResults.get(0);

        /*
         * 초 → 분
         */
        int averageTravelTime =
                (int) Math.round(
                        routeResult.averageTravelSeconds() / 60.0
                );

        /*
         * 장소 이미지 URL 생성
         */
        List<String> imageUrls =
                details.photos() == null
                        ? List.of()
                        : details.photos().stream()
                        .limit(3)
                        .map(GooglePlaceDetailsResponse.Photo::name)
                        .map(googlePlacesClient::getPlacePhotoUrl)
                        .filter(url -> url != null && !url.isBlank())
                        .toList();

        return new RecommendedPlaceDetailResponse(
                recommendedPlace.getId(),
                extractKoreanPlaceName(details.displayName().text()),
                recommendedPlace.getCategory(),
                details.formattedAddress(),
                recommendedArea.getAreaName(),
                participants.size(),
                recommendedPlace.getPreferenceMatchCnt(),
                averageTravelTime,
                imageUrls
        );
    }

    private RecommendedPlaceListResponse.Place toPlaceResponse(
            RecommendedPlace recommendedPlace,
            GooglePlaceDetailsResponse details,
            int participantCount,
            PlaceRouteResult routeResult
    ) {
        /*
         * 선호 일치율
         *
         * ex)
         * 참가자 4명 중 3명 일치
         * → 75%
         */
        int preferenceMatchRate =
                participantCount == 0
                        ? 0
                        : (int) Math.round(
                        (double) recommendedPlace.getPreferenceMatchCnt()
                                / participantCount
                                * 100
                );

        /*
         * Google Routes 결과 초 → 분
         */
        int averageTravelTime =
                (int) Math.round(
                        routeResult.averageTravelSeconds() / 60.0
                );

        int maxTravelTime =
                (int) Math.round(
                        routeResult.maxTravelSeconds() / 60.0
                );

        return new RecommendedPlaceListResponse.Place(
                recommendedPlace.getId(),
                extractKoreanPlaceName(details.displayName().text()),
                recommendedPlace.getCategory(),
                preferenceMatchRate,
                averageTravelTime,
                maxTravelTime,
                recommendedPlace.getPreferenceMatchCnt()
        );
    }

    /*
     * ParticipantPreferenceRepository의 IN 조회 결과는
     * 순서가 보장되지 않으므로 Participant 순서에 맞춰 재정렬
     */
    private List<ParticipantPreference> getOrderedParticipantPreferences(
            List<Participant> participants
    ) {
        List<Long> participantIds =
                participants.stream()
                        .map(Participant::getId)
                        .toList();

        List<ParticipantPreference> preferences =
                participantPreferenceRepository.findAllByParticipantIdIn(
                        participantIds
                );

        Map<Long, ParticipantPreference> preferenceMap =
                preferences.stream()
                        .collect(Collectors.toMap(
                                ParticipantPreference::getParticipantId,
                                Function.identity()
                        ));

        return participants.stream()
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
    }

    private String extractKoreanPlaceName(String displayName) {
        if (displayName == null) {
            return null;
        }

        return displayName.split("\\|")[0].trim();
    }

    private void validateRecommendedArea(
            Long meetingId,
            Long recommendedAreaId
    ) {
        RecommendedArea recommendedArea =
                recommendedAreaRepository.findById(recommendedAreaId)
                        .orElseThrow(() ->
                                new CustomException(
                                        CustomErrorCode.RECOMMENDED_AREA_NOT_FOUND
                                )
                        );

        RecommendationRun recommendationRun =
                recommendationRunRepository.findById(
                                recommendedArea.getRecommendationRunId()
                        )
                        .orElseThrow(() ->
                                new CustomException(
                                        CustomErrorCode.RECOMMENDATION_RESULT_NOT_FOUND
                                )
                        );

        if (!recommendationRun.getMeetingId().equals(meetingId)) {
            throw new CustomException(
                    CustomErrorCode.RECOMMENDED_AREA_NOT_FOUND
            );
        }
    }
}
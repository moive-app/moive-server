package com.moive.MoiveBE.domain.route.service;

import com.moive.MoiveBE.domain.meeting.entity.Meeting;
import com.moive.MoiveBE.domain.meeting.entity.MeetingStatus;
import com.moive.MoiveBE.domain.meeting.entity.Participant;
import com.moive.MoiveBE.domain.meeting.entity.ParticipantPreference;
import com.moive.MoiveBE.domain.meeting.repository.MeetingRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantPreferenceRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantRepository;
import com.moive.MoiveBE.domain.recommendation.client.GooglePlacesClient;
import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceLocationResponse;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendedPlace;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendedPlaceRepository;
import com.moive.MoiveBE.domain.route.dto.Location;
import com.moive.MoiveBE.domain.route.dto.MeetingDetailResponse;
import com.moive.MoiveBE.domain.route.dto.RouteDetail;
import com.moive.MoiveBE.domain.user.entity.User;
import com.moive.MoiveBE.domain.user.repository.UserRepository;
import com.moive.MoiveBE.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.moive.MoiveBE.global.exception.CustomErrorCode.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeetingRouteService {

    private final MeetingRepository meetingRepository;
    private final ParticipantRepository participantRepository;
    private final ParticipantPreferenceRepository participantPreferenceRepository;
    private final UserRepository userRepository;
    private final RecommendedPlaceRepository recommendedPlaceRepository;

    private final GooglePlacesClient googlePlacesClient;
    private final RouteDetailService routeDetailService;

    /**
     * 확정된 모임 상세 정보 조회
     * - 기준 1: 모임 종료 여부 (종료, 진행 전)
     * - 기준 2: 최종 확정 장소 존재 여부 (전원 미투표, 그 외)
     */
    public MeetingDetailResponse getMeetingDetail(Long meetingId, Long userId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new CustomException(MEETING_NOT_FOUND));

        // 모임 상세 조회 가능 조건 검증
        validateConfirmed(meeting);
        validateParticipant(meetingId, userId);

        // 모임 종료 여부 확인 (모임 일시가 현재 시각을 지났으면 종료)
        LocalDateTime meetingDateTime =
                LocalDateTime.of(meeting.getScheduledDate(), meeting.getScheduledTime());
        boolean isEnded = !meetingDateTime.isAfter(LocalDateTime.now());

        // 확정된 장소 존재 여부 확인, 있다면 장소 정보 조회
        Long confirmedPlaceId = meeting.getConfirmedPlaceId();
        MeetingDetailResponse.Place placeInfo = fetchPlaceInfo(confirmedPlaceId);

        // 참여자 목록 조회 & 이동 정보 조회(조건: 모임 진행 전(!isEnded) + 확정된 장소 있음 + 구글맵 장소 조회 성공)
        List<MeetingDetailResponse.ParticipantInfo> participants =
                getParticipantsInfo(meetingId, confirmedPlaceId, isEnded, placeInfo);

        return MeetingDetailResponse.of(isEnded, meeting, placeInfo, participants);
    }

    /**
     * 모임 상세 조회 가능 조건 검증
     */
    private void validateConfirmed(Meeting meeting) {
        MeetingStatus status = meeting.getStatus();
        boolean confirmed = (status == MeetingStatus.CONFIRMED || status == MeetingStatus.COMPLETED)
                && meeting.getScheduledDate() != null
                && meeting.getScheduledTime() != null;
        if (!confirmed) {
            throw new CustomException(MEETING_NOT_CONFIRMED);
        }
    }

    private void validateParticipant(Long meetingId, Long userId) {
        participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(meetingId, userId)
                .orElseThrow(() -> new CustomException(MEETING_ACCESS_DENIED));
    }

    /**
     * 확정된 장소의 정보 조회
     * - 구글맵 API 호출
     * - 조회 실패 => place 내 isFetchFailed=true, name/address/category/location null
     */
    private MeetingDetailResponse.Place fetchPlaceInfo(Long confirmedPlaceId) {
        // 확정된 장소가 없다면(전원 미투표) null
        if(confirmedPlaceId == null) {
            return null;
        }

        RecommendedPlace recommendedPlace = recommendedPlaceRepository.findById(confirmedPlaceId)
                .orElseThrow(() -> new CustomException(RECOMMENDED_PLACE_NOT_FOUND));

        GooglePlaceLocationResponse googlePlace;
        try {
            googlePlace = googlePlacesClient.getPlaceLocation(recommendedPlace.getGooglePlaceId());
        } catch (CustomException e) {
            log.warn("[모임 상세] 구글 장소 조회 실패 => isFetchFailed=true, confirmedPlaceId={}, errorCode={}",
                    confirmedPlaceId, e.getCustomErrorCode());
            return MeetingDetailResponse.Place.fetchFailed(confirmedPlaceId);
        }

        if (googlePlace == null || googlePlace.location() == null) {
            log.warn("[모임 상세] 구글 장소 조회 응답이 비어있음 => isFetchFailed=true, confirmedPlaceId={}", confirmedPlaceId);
            return MeetingDetailResponse.Place.fetchFailed(confirmedPlaceId);
        }
        return MeetingDetailResponse.Place.of(confirmedPlaceId, googlePlace, recommendedPlace.getCategory());
    }

    /**
     * 참여자 목록, 출발 위치 조회
     * - Participant, ParticipantPreference 조회
     */
    private List<MeetingDetailResponse.ParticipantInfo> getParticipantsInfo(
            Long meetingId,
            Long confirmedPlaceId,
            boolean isEnded,
            MeetingDetailResponse.Place placeInfo
    ) {
        // 모임 진행 전 + 확정된 장소 없음(전원 미투표) => 빈 리스트 반환
        if (!isEnded && confirmedPlaceId == null) {
            return List.of();
        }

        // 모임 참여자 정보 조회
        List<ParticipantDetail> participantDetails = getParticipantDetails(meetingId);

        // 이동 정보 조회 (모임 진행 전(!isEnded) + 확정된 장소 있음 + 구글맵 장소 조회 성공)
        Map<Long, RouteDetail> routeByParticipantId = Map.of();
        if (!isEnded && placeInfo != null && !placeInfo.isFetchFailed()) {
            routeByParticipantId = fetchRouteInfo(participantDetails, placeInfo);
        }

        return toParticipantInfos(participantDetails, routeByParticipantId);
    }

    private List<ParticipantDetail> getParticipantDetails(Long meetingId) {
        // 참여자 목록 (joinedAt 오름차순)
        List<Participant> participants =
                participantRepository.findAllByMeetingIdAndLeftAtIsNullOrderByJoinedAtAsc(meetingId);

        // 유저 IN 배치 조회 -> Map<userId, User>
        List<Long> userIds = participants.stream().map(Participant::getUserId).toList();
        Map<Long, User> userById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        // 출발지 선호조건 IN 배치 조회 -> Map<participantId, ParticipantPreference>
        List<Long> participantIds = participants.stream().map(Participant::getId).toList();
        Map<Long, ParticipantPreference> preferenceByParticipantId =
                participantPreferenceRepository.findAllByParticipantIdIn(participantIds).stream()
                        .collect(Collectors.toMap(ParticipantPreference::getParticipantId, Function.identity()));

        return participants.stream()
                .map(participant -> new ParticipantDetail(
                        participant,
                        userById.get(participant.getUserId()),
                        preferenceByParticipantId.get(participant.getId())
                ))
                .toList();
    }

    /**
     * 참여자별 (출발지 -> 모임 장소) 대중교통 이동 정보 조회
     * - 카카오맵 대중교통 경로 조회 API 호출
     * - 대중교통 경로 없음 or 조회 실패 => transferCnt/totalTime = null
     */
    private Map<Long, RouteDetail> fetchRouteInfo(
            List<ParticipantDetail> participantDetails,
            MeetingDetailResponse.Place place
    ) {
        Location placeLocation = place.location();
        String placeName = (place.name() != null) ? place.name() : "모임 장소";

        Map<Long, RouteDetail> routeByParticipantId = new LinkedHashMap<>();
        for (ParticipantDetail detail : participantDetails) {
            ParticipantPreference preference = detail.preference();
            if (preference == null) {
                continue;
            }

            Location departure = new Location(
                    preference.getDepartureLatitude().doubleValue(),
                    preference.getDepartureLongitude().doubleValue()
            );

            try {
                RouteDetail routeDetail = routeDetailService.getMyRouteDetail(
                        departure, placeLocation, preference.getDepartureName(), placeName
                );
                routeByParticipantId.put(detail.participant().getId(), routeDetail);
            } catch (CustomException e) {
                log.info("[모임 상세] 참여자 이동 경로 조회 실패 => 이동 정보(transferCnt/totalTime) null (participantId={}, errorCode={})",
                        detail.participant().getId(), e.getCustomErrorCode());
            }
        }
        return routeByParticipantId;
    }

    /**
     * 응답 구성
     */
    private List<MeetingDetailResponse.ParticipantInfo> toParticipantInfos(
            List<ParticipantDetail> participantDetails,
            Map<Long, RouteDetail> routeByParticipantId
    ) {
        return participantDetails.stream()
                .map(detail -> MeetingDetailResponse.ParticipantInfo.of(
                        detail.user(),
                        detail.preference(),
                        routeByParticipantId.get(detail.participant().getId())
                ))
                .toList();
    }

    private record ParticipantDetail (
            Participant participant,
            User user,
            ParticipantPreference preference
    ) {
    }
}

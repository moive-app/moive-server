package com.moive.MoiveBE.domain.route.service;

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
import com.moive.MoiveBE.domain.route.dto.RouteDetail;
import com.moive.MoiveBE.domain.route.dto.RouteDetailResponse;
import com.moive.MoiveBE.domain.user.entity.User;
import com.moive.MoiveBE.domain.user.repository.UserRepository;
import com.moive.MoiveBE.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.moive.MoiveBE.global.exception.CustomErrorCode.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RouteService {

    private final UserRepository userRepository;
    private final MeetingRepository meetingRepository;
    private final ParticipantRepository participantRepository;
    private final ParticipantPreferenceRepository participantPreferenceRepository;
    private final RecommendedPlaceRepository recommendedPlaceRepository;
    private final GooglePlacesClient googlePlacesClient;

    private final RouteDetailService routeDetailService;

    public RouteDetailResponse getRecommendedPlaceRoute(
            Long userId,
            Long meetingId,
            Long recommendedPlaceId
    ) {
        // 유저 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(USER_NOT_FOUND));

        // 모임 조회
        meetingRepository.findById(meetingId)
                .orElseThrow(() -> new CustomException(MEETING_NOT_FOUND));

        // 모임 참여자 조회
        Participant participant = participantRepository.findByMeetingIdAndUserId(meetingId, userId)
                .orElseThrow(() -> new CustomException(PARTICIPANT_NOT_FOUND));
        ParticipantPreference preference = participantPreferenceRepository.findByParticipantId(participant.getId())
                .orElseThrow(() -> new CustomException(PARTICIPANT_PREFERENCE_NOT_FOUND));
        Location userLocation = new Location(
                preference.getDepartureLatitude().doubleValue(),
                preference.getDepartureLongitude().doubleValue()
        );

        // 추천 장소 조회
        RecommendedPlace place = recommendedPlaceRepository.findById(recommendedPlaceId)
                .orElseThrow(() -> new CustomException(RECOMMENDED_PLACE_NOT_FOUND));

        // 구글맵 API 호출 => 추천 장소 위치 정보 조회
        GooglePlaceLocationResponse placeInfo = googlePlacesClient.getPlaceLocation(place.getGooglePlaceId());
        Location placeLocation = new Location(placeInfo.location().latitude(), placeInfo.location().longitude());

        // 카카오맵 대중교통 경로 조회 => 이동 경로 관련 정보 생성
        RouteDetail routeDetail = routeDetailService.getMyRouteDetail(
                userLocation, placeLocation,
                preference.getDepartureName(), placeInfo.displayName().text()
        );

        return RouteDetailResponse.of(
                user.getNickname(),
                userLocation,
                placeLocation,
                routeDetail
        );
    }

}

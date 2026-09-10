package com.moive.MoiveBE.domain.route.dto;

import com.moive.MoiveBE.domain.meeting.entity.Meeting;
import com.moive.MoiveBE.domain.meeting.entity.ParticipantPreference;
import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceLocationResponse;
import com.moive.MoiveBE.domain.user.entity.User;
import lombok.AccessLevel;
import lombok.Builder;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Builder(access = AccessLevel.PRIVATE)
public record MeetingDetailResponse(
        String status,               // CONFIRMED | ENDED
        Place place,                 // 전원 미투표 => null
        String meetingDate,          // yyyy-MM-dd
        String meetingTime,          // HH:mm
        List<ParticipantInfo> participants
) {

    private static final String STATUS_CONFIRMED = "CONFIRMED";
    private static final String STATUS_ENDED = "ENDED";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public static MeetingDetailResponse of(
            boolean isEnded,
            Meeting meeting,
            Place place,
            List<ParticipantInfo> participants
    ) {
        return MeetingDetailResponse.builder()
                .status(isEnded ? STATUS_ENDED : STATUS_CONFIRMED)
                .place(place)
                .meetingDate(meeting.getScheduledDate().toString())
                .meetingTime(meeting.getScheduledTime().format(TIME_FORMATTER))
                .participants(participants)
                .build();
    }

    @Builder(access = AccessLevel.PRIVATE)
    public record Place(
            Long id,
            boolean isFetchFailed,   // 구글맵 장소 조회 실패 => true
            String name,
            String address,
            String category,
            Location location
    ) {

        // 구글맵 장소 조회 성공
        public static Place of(Long placeId, GooglePlaceLocationResponse googlePlace) {
            return Place.builder()
                    .id(placeId)
                    .isFetchFailed(false)
                    .name(textOrNull(googlePlace.displayName()))
                    .address(googlePlace.formattedAddress())
                    .category(textOrNull(googlePlace.primaryTypeDisplayName()))
                    .location(new Location(
                            googlePlace.location().latitude(),
                            googlePlace.location().longitude()
                    ))
                    .build();
        }

        // 구글맵 장소 조회 실패
        public static Place fetchFailed(Long placeId) {
            return Place.builder()
                    .id(placeId)
                    .isFetchFailed(true)
                    .build();
        }

        private static String textOrNull(GooglePlaceLocationResponse.LocalizedText text) {
            return (text != null) ? text.text() : null;
        }
    }

    @Builder(access = AccessLevel.PRIVATE)
    public record ParticipantInfo(
            String profileImageUrl,
            String nickname,
            String address,
            Integer transferCnt,     // ENDED or 이동 경로 조회 실패 => null
            Integer totalTime        // ENDED or 이동 경로 조회 실패 => null
    ) {
        public static ParticipantInfo of(User user, ParticipantPreference preference, RouteDetail route) {
            return ParticipantInfo.builder()
                    .profileImageUrl(user.getProfileImageUrl())
                    .nickname(user.getNickname())
                    .address(preference.getDepartureName())
                    .transferCnt(route != null ? route.transferCnt() : null)
                    .totalTime(route != null ? route.totalTime() : null)
                    .build();
        }
    }
}

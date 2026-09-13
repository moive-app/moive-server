package com.moive.MoiveBE.domain.route.dto;

import com.moive.MoiveBE.domain.meeting.entity.Meeting;
import com.moive.MoiveBE.domain.meeting.entity.MeetingStatus;
import com.moive.MoiveBE.domain.meeting.entity.ParticipantPreference;
import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceLocationResponse;
import com.moive.MoiveBE.domain.user.entity.User;
import lombok.AccessLevel;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Builder(access = AccessLevel.PRIVATE)
public record MeetingDetailResponse(
        String status,               // CONFIRMED | COMPLETED
        Place place,                 // 전원 미투표 => null
        LocalDate meetingDate,       // yyyy-MM-dd
        LocalTime meetingTime,       // HH:mm
        List<ParticipantInfo> participants
) {

    public static MeetingDetailResponse of(
            boolean isEnded,
            Meeting meeting,
            Place place,
            List<ParticipantInfo> participants
    ) {
        return MeetingDetailResponse.builder()
                .status(isEnded ? MeetingStatus.COMPLETED.name() : MeetingStatus.CONFIRMED.name())
                .place(place)
                .meetingDate(meeting.getScheduledDate())
                .meetingTime(meeting.getScheduledTime())
                .participants(participants)
                .build();
    }

    @Builder(access = AccessLevel.PRIVATE)
    public record Place(
            Long id,
            Long areaId,
            boolean isFetchFailed,   // 구글맵 장소 조회 실패 => true
            String name,
            String address,
            String category,
            Location location
    ) {

        // 구글맵 장소 조회 성공
        public static Place of(Long placeId, Long placeAreaId, GooglePlaceLocationResponse googlePlace, String category) {
            return Place.builder()
                    .id(placeId)
                    .areaId(placeAreaId)
                    .isFetchFailed(false)
                    .name(textOrNull(googlePlace.displayName()))
                    .address(googlePlace.formattedAddress())
                    .category(category)
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
            Integer transferCnt,     // COMPLETED or 이동 경로 조회 실패 => null
            Integer totalTime        // COMPLETED or 이동 경로 조회 실패 => null
    ) {
        public static ParticipantInfo of(User user, ParticipantPreference preference, RouteDetail route) {
            return ParticipantInfo.builder()
                    .profileImageUrl(user.getProfileImageUrl())
                    .nickname(user.getNickname())
                    .address(preference != null ? preference.getDepartureName() : null)
                    .transferCnt(route != null ? route.transferCnt() : null)
                    .totalTime(route != null ? route.totalTime() : null)
                    .build();
        }
    }
}

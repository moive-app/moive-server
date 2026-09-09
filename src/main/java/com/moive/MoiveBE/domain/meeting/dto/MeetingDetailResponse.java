package com.moive.MoiveBE.domain.meeting.dto;

import java.util.List;

public record MeetingDetailResponse(
        Long meetingId,
        String name,
        String purposeType,
        String status,
        String statusLabel,
        String inviteCode,
        String inviteUrl,
        boolean canShare,
        boolean recommendationReady,
        String myParticipantState,
        String myParticipantStateLabel,
        List<ParticipantDto> participants,
        ConfirmedPlaceDto confirmedPlace,
        String confirmedDate,
        String confirmedTime,
        TravelSummaryDto travelSummary
) {
    public record ParticipantDto(
            Long participantId,
            Long userId,
            String nickname,
            String profileImageUrl,
            String participantState,
            String participantStateLabel,
            boolean excludedFromRecommendation,
            Integer travelMinutes
    ) {}

    public record ConfirmedPlaceDto(
            Long placeId,
            String placeName,
            String category,
            String address,
            Double latitude,
            Double longitude
    ) {}

    public record TravelSummaryDto(
            Integer avgMinutes,
            Integer maxMinutes
    ) {}
}

package com.moive.MoiveBE.domain.meeting.dto;

import java.util.List;

public record MeetingHomeResponse(
        Long meetingId,
        String name,
        String purposeType,
        String status,
        boolean hasSchedule,
        String scheduledDate,
        String scheduledTime,
        String inviteCode,
        String inviteUrl,
        List<ParticipantDto> participants,
        String homeMessage,
        String primaryActionLabel,
        boolean primaryActionEnabled
) {
    public record ParticipantDto(
            Long participantId,
            String nickname,
            String profileImageUrl,
            String participantState,
            String participantStateLabel,
            boolean isMe
    ) {}
}

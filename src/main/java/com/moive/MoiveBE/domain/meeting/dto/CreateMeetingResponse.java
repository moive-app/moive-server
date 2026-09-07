package com.moive.MoiveBE.domain.meeting.dto;

import com.moive.MoiveBE.domain.meeting.entity.Meeting;
import com.moive.MoiveBE.domain.meeting.entity.PurposeType;

public record CreateMeetingResponse(
        Long meetingId,
        String name,
        boolean hasSchedule,
        String scheduledDate,
        String scheduledTime,
        String purposeType,
        String status,
        String inviteCode,
        String inviteUrl,
        String createdAt
) {
    public static CreateMeetingResponse from(Meeting meeting, PurposeType purposeType, String inviteUrl) {
        boolean hasSchedule = meeting.getScheduledDate() != null;
        return new CreateMeetingResponse(
                meeting.getId(),
                meeting.getName(),
                hasSchedule,
                hasSchedule ? meeting.getScheduledDate().toString() : null,
                hasSchedule ? meeting.getScheduledTime().toString() : null,
                purposeType.name(),
                meeting.getStatus().name(),
                meeting.getInviteCode(),
                inviteUrl,
                meeting.getCreatedAt().toString()
        );
    }
}
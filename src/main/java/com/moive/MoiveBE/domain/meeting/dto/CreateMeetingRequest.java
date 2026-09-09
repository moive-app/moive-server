package com.moive.MoiveBE.domain.meeting.dto;

public record CreateMeetingRequest(
        String name,
        Boolean hasSchedule,
        String scheduledDate,
        String scheduledTime,
        String purposeType
) {
}
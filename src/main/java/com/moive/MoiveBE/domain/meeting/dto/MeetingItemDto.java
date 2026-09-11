package com.moive.MoiveBE.domain.meeting.dto;

public record MeetingItemDto(
        Long meetingId,
        String name,
        String purposeType,
        String status,
        String statusLabel,
        String scheduledDate,
        String scheduledTime,
        int participantCnt,
        int submittedCnt
) {}

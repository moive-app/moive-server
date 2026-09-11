package com.moive.MoiveBE.domain.meeting.dto;

import java.util.List;

public record MeetingItemDto(
        Long meetingId,
        String name,
        String purposeType,
        String status,
        String statusLabel,
        String scheduledDate,
        String scheduledTime,
        int participantCnt,
        List<String> participantImages
) {}

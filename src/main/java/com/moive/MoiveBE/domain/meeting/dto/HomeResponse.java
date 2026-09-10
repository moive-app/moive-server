package com.moive.MoiveBE.domain.meeting.dto;

import java.util.List;

public record HomeResponse(
        List<ConfirmedMeetingDto> confirmedMeetings,
        List<MeetingItemDto> myMeetings
) {
    public record ConfirmedMeetingDto(
            Long meetingId,
            String name,
            String confirmedPlaceName,
            String confirmedDate,
            String confirmedTime,
            List<String> participantProfileImages,
            int participantCnt,
            int dDay
    ) {}
}

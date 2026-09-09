package com.moive.MoiveBE.domain.meeting.dto;

import java.util.List;

public record MeetingListResponse(
        List<MeetingItemDto> meetings,
        boolean hasNext,
        Long nextCursor
) {}

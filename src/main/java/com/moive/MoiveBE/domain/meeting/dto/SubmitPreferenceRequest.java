package com.moive.MoiveBE.domain.meeting.dto;

import com.moive.MoiveBE.domain.meeting.entity.ActivityType;

import java.math.BigDecimal;
import java.util.List;

public record SubmitPreferenceRequest(
        List<AvailableSchedule> availableSchedules,
        String departureName,
        BigDecimal departureLatitude,
        BigDecimal departureLongitude,
        Integer maxTravelMinutes,
        List<ActivityType> activityTypes
) {
    public record AvailableSchedule(
            String date,
            String time
    ) {}
}

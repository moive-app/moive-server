package com.moive.MoiveBE.domain.meeting.dto;

import com.moive.MoiveBE.domain.meeting.entity.MeetingStatus;
import com.moive.MoiveBE.domain.meeting.entity.ParticipantState;

public record SubmitPreferenceResponse(
        Long meetingId,
        Long participantId,
        String participantState,
        String participantStateLabel,
        String meetingStatus,
        boolean recommendationTriggered
) {
    public static SubmitPreferenceResponse of(
            Long meetingId,
            Long participantId,
            ParticipantState participantState,
            MeetingStatus meetingStatus,
            boolean recommendationTriggered
    ) {
        return new SubmitPreferenceResponse(
                meetingId,
                participantId,
                participantState.name(),
                participantState.getLabel(),
                meetingStatus.name(),
                recommendationTriggered
        );
    }
}

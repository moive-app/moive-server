package com.moive.MoiveBE.domain.meeting.dto;

import com.moive.MoiveBE.domain.meeting.entity.ParticipantState;

public record JoinMeetingResponse(
        Long meetingId,
        Long participantId,
        String participantState,
        String participantStateLabel,
        boolean alreadyParticipant
) {
    public static JoinMeetingResponse of(
            Long meetingId,
            Long participantId,
            ParticipantState state,
            boolean alreadyParticipant
    ) {
        return new JoinMeetingResponse(
                meetingId,
                participantId,
                state.name(),
                state.getLabel(),
                alreadyParticipant
        );
    }
}
package com.moive.MoiveBE.domain.vote.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record DateVoteSummary(
        LocalDate candidateDate,
        LocalTime candidateTime,
        Long voterCnt,
        Long votedByMe
) {
    public boolean isVotedByMe() {
        return votedByMe != null && votedByMe > 0;
    }
}

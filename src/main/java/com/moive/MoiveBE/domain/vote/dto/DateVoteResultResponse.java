package com.moive.MoiveBE.domain.vote.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record DateVoteResultResponse(
        boolean isVoteSkipped,
        Integer totalVoterCnt,       // 일정 확정 => null
        List<Candidate> candidates
) {
    public static DateVoteResultResponse of(
            boolean isVoteSkipped,
            Integer totalVoterCnt,
            List<Candidate> candidates
    ) {
        return new DateVoteResultResponse(isVoteSkipped, totalVoterCnt, candidates);
    }

    public record Candidate(
            @JsonFormat(pattern = "yyyy-MM-dd")
            LocalDate meetingDate,
            @JsonFormat(pattern = "HH:mm")
            LocalTime meetingTime,
            Integer voterCnt,         // 일정 확정 => null
            boolean isVotedByMe       // 일정 확정 => false
    ) {
        public static Candidate of(
                LocalDate meetingDate,
                LocalTime meetingTime,
                Integer voterCnt,
                boolean isVotedByMe
        ) {
            return new Candidate(meetingDate, meetingTime, voterCnt, isVotedByMe);
        }
    }
}

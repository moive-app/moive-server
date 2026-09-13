package com.moive.MoiveBE.domain.vote.dto;

import java.util.List;

public record PlaceVoteResultResponse(
        boolean isFinished,
        int totalVoterCnt,
        List<Candidate> candidates
) {
    public static PlaceVoteResultResponse of(boolean isFinished, int totalVoterCnt, List<Candidate> candidates) {
        return new PlaceVoteResultResponse(isFinished, totalVoterCnt, candidates);
    }

    public record Candidate(
            Long placeId,
            Long placeAreaId,
            String placeName,
            int voterCnt,
            boolean isVotedByMe
    ) {
        public static Candidate of(
                Long placeId,
                Long placeAreaId,
                String placeName,
                int voterCnt,
                boolean isVotedByMe
        ) {
            return new Candidate(placeId, placeAreaId, placeName, voterCnt, isVotedByMe);
        }
    }
}

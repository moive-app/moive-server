package com.moive.MoiveBE.domain.vote.dto;

public record PlaceVoteSummary(
        Long recommendedPlaceId,
        Long voterCnt,
        Long votedByMe
) {
    public boolean isVotedByMe() {
        return votedByMe != null && votedByMe > 0;
    }
}

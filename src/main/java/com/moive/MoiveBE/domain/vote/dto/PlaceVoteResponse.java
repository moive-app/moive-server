package com.moive.MoiveBE.domain.vote.dto;

public record PlaceVoteResponse(
        boolean isConfirmed
) {
    public static PlaceVoteResponse of(boolean isConfirmed) {
        return new PlaceVoteResponse(isConfirmed);
    }
}

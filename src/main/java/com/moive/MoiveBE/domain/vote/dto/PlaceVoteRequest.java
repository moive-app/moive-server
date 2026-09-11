package com.moive.MoiveBE.domain.vote.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PlaceVoteRequest(
        @NotEmpty(message = "최소 1개의 장소를 선택해야 합니다.")
        List<Long> recommendedPlaceIds
) {
}

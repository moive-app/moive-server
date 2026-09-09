package com.moive.MoiveBE.domain.user.dto;

public record MyInfoResponse(
        String nickname,
        String profileImageUrl,
        String email
) {
}
package com.moive.MoiveBE.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KakaoUserInfoResponse(
        Long id,
        Properties properties,
        @JsonProperty("kakao_account")
        KakaoAccount kakaoAccount
) {

    public record Properties(
            String nickname,
            @JsonProperty("profile_image")
            String profileImage
    ) {
    }

    public record KakaoAccount(
            String email
    ) {
    }
}
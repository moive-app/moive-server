package com.moive.MoiveBE.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record KakaoLoginResponse(

        @Schema(
                description = "MOIVE 가입 여부",
                example = "false"
        )
        boolean registered,

        @Schema(
                description = "카카오 닉네임",
                example = "한재경"
        )
        String nickname,

        @Schema(
                description = "카카오 프로필 이미지 URL",
                example = "https://example.com/profile.jpg"
        )
        String profileImageUrl,

        @Schema(
                description = "카카오 이메일",
                example = "example@kakao.com"
        )
        String email

) {
}
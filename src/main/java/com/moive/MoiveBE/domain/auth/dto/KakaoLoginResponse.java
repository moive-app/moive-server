package com.moive.MoiveBE.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class KakaoLoginResponse {

    @Schema(
            description = "MOIVE 가입 여부",
            example = "false"
    )
    private boolean registered;

    @Schema(
            description = "카카오 닉네임",
            example = "한재경"
    )
    private String nickname;

    @Schema(
            description = "카카오 프로필 이미지 URL",
            example = "https://example.com/profile.jpg"
    )
    private String profileImageUrl;

    @Schema(
            description = "카카오 계정 이메일",
            example = "example@kakao.com"
    )
    private String email;
}
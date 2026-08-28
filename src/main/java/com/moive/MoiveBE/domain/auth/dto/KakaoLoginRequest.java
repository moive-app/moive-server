package com.moive.MoiveBE.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record KakaoLoginRequest(

        @NotBlank(message = "카카오 Access Token은 필수입니다.")
        @Schema(
                description = "카카오 SDK에서 발급받은 Access Token",
                example = "kakao-access-token"
        )
        String accessToken

) {
}
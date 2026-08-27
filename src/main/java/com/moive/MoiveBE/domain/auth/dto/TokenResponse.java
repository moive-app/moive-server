package com.moive.MoiveBE.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TokenResponse(

        @Schema(
                description = "MOIVE Access Token",
                example = "eyJhbGciOiJIUzI1NiJ9..."
        )
        String accessToken,

        @Schema(
                description = "MOIVE Refresh Token",
                example = "eyJhbGciOiJIUzI1NiJ9..."
        )
        String refreshToken

) {
}
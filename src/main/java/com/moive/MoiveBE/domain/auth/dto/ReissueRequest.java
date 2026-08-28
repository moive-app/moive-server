package com.moive.MoiveBE.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record ReissueRequest(

        @NotBlank(message = "Refresh Token은 필수입니다.")
        @Schema(
                description = "MOIVE Refresh Token",
                example = "eyJhbGciOiJIUzI1NiJ9..."
        )
        String refreshToken

) {
}
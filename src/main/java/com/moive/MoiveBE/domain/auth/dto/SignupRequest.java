package com.moive.MoiveBE.domain.auth.dto;

import com.moive.MoiveBE.domain.user.entity.AgreementType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SignupRequest(

        @NotBlank
        String accessToken,

        @NotEmpty
        @Valid
        List<AgreementRequest> agreements
) {

    public record AgreementRequest(

            @NotNull
            AgreementType type,

            @NotBlank
            String version,

            boolean agreed
    ) {
    }
}
package com.moive.MoiveBE.domain.auth.dto;

import com.moive.MoiveBE.domain.user.entity.AgreementType;

import java.util.List;

public record SignupRequest(
        String accessToken,
        List<AgreementRequest> agreements
) {

    public record AgreementRequest(
            AgreementType type,
            String version,
            boolean agreed
    ) {
    }
}
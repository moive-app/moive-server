package com.moive.MoiveBE.domain.auth.dto;

import com.moive.MoiveBE.domain.user.entity.AgreementType;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class SignupRequest {

    private String accessToken;

    private List<AgreementRequest> agreements;

    @Getter
    @NoArgsConstructor
    public static class AgreementRequest {

        private AgreementType type;

        private String version;

        private boolean agreed;
    }
}
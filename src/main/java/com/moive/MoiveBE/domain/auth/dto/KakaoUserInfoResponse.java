package com.moive.MoiveBE.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class KakaoUserInfoResponse {

    private Long id;

    private Properties properties;

    @JsonProperty("kakao_account")
    private KakaoAccount kakaoAccount;

    @Getter
    @NoArgsConstructor
    public static class Properties {

        private String nickname;

        @JsonProperty("profile_image")
        private String profileImage;
    }

    @Getter
    @NoArgsConstructor
    public static class KakaoAccount {

        private String email;
    }
}
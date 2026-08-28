package com.moive.MoiveBE.domain.auth.service;

import com.moive.MoiveBE.domain.auth.dto.KakaoUserInfoResponse;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
@RequiredArgsConstructor
public class KakaoAuthService {

    private final RestClient restClient;

    public KakaoUserInfoResponse getUserInfo(String accessToken) {
        try {
            return restClient.get()
                    .uri("https://kapi.kakao.com/v2/user/me")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(KakaoUserInfoResponse.class);

        } catch (RestClientResponseException e) {
            throw new CustomException(
                    CustomErrorCode.INVALID_KAKAO_TOKEN
            );
        } catch (Exception e) {
            throw new CustomException(
                    CustomErrorCode.KAKAO_API_ERROR
            );
        }
    }
}
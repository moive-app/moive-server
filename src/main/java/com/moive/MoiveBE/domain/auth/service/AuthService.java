package com.moive.MoiveBE.domain.auth.service;

import com.moive.MoiveBE.domain.auth.dto.KakaoLoginResponse;
import com.moive.MoiveBE.domain.auth.dto.KakaoUserInfoResponse;
import com.moive.MoiveBE.domain.user.entity.UserStatus;
import com.moive.MoiveBE.domain.user.repository.UserRepository;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final KakaoAuthService kakaoAuthService;
    private final UserRepository userRepository;

    public KakaoLoginResponse loginWithKakao(String accessToken) {

        // 1. Kakao Access Token으로 카카오 사용자 정보 조회
        KakaoUserInfoResponse kakaoUser =
                kakaoAuthService.getUserInfo(accessToken);

        // 2. 카카오 필수 사용자 정보 검증
        validateKakaoUserInfo(kakaoUser);

        // 3. Kakao Member ID로 기존 활성 회원 조회
        boolean registered = userRepository
                .findByKakaoMemberIdAndStatus(
                        kakaoUser.getId(),
                        UserStatus.ACTIVE
                )
                .isPresent();

        // 4. 기존/신규 여부와 카카오 프로필 정보 반환
        return new KakaoLoginResponse(
                registered,
                kakaoUser.getProperties().getNickname(),
                kakaoUser.getProperties().getProfileImage(),
                kakaoUser.getKakaoAccount().getEmail()
        );
    }

    private void validateKakaoUserInfo(KakaoUserInfoResponse kakaoUser) {

        if (kakaoUser == null
                || kakaoUser.getId() == null
                || kakaoUser.getProperties() == null
                || kakaoUser.getProperties().getNickname() == null
                || kakaoUser.getProperties().getProfileImage() == null
                || kakaoUser.getKakaoAccount() == null
                || kakaoUser.getKakaoAccount().getEmail() == null) {

            throw new CustomException(
                    CustomErrorCode.KAKAO_REQUIRED_INFO_MISSING
            );
        }
    }
}
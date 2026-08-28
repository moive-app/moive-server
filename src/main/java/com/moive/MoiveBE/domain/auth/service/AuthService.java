package com.moive.MoiveBE.domain.auth.service;

import com.moive.MoiveBE.domain.auth.dto.KakaoLoginResponse;
import com.moive.MoiveBE.domain.auth.dto.KakaoUserInfoResponse;
import com.moive.MoiveBE.domain.user.entity.UserStatus;
import com.moive.MoiveBE.domain.user.repository.UserRepository;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import com.moive.MoiveBE.domain.auth.dto.SignupRequest;
import com.moive.MoiveBE.domain.user.entity.AgreementType;
import com.moive.MoiveBE.domain.user.entity.User;
import com.moive.MoiveBE.domain.user.entity.UserAgreement;
import com.moive.MoiveBE.domain.user.repository.UserAgreementRepository;
import com.moive.MoiveBE.domain.auth.dto.TokenResponse;
import com.moive.MoiveBE.domain.auth.dto.ReissueRequest;
import com.moive.MoiveBE.domain.auth.dto.LogoutRequest;
import com.moive.MoiveBE.global.jwt.JwtTokenProvider;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final KakaoAuthService kakaoAuthService;
    private final UserRepository userRepository;
    private final UserAgreementRepository userAgreementRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public KakaoLoginResponse loginWithKakao(String accessToken) {

        // 1. Kakao Access Token으로 카카오 사용자 정보 조회
        KakaoUserInfoResponse kakaoUser =
                kakaoAuthService.getUserInfo(accessToken);

        // 2. 카카오 필수 사용자 정보 검증
        validateKakaoUserInfo(kakaoUser);

        // 3. Kakao Member ID로 기존 활성 회원 조회
        Optional<User> existingUser = userRepository
                .findByKakaoMemberIdAndStatus(
                        kakaoUser.id(),
                        UserStatus.ACTIVE
                );

        // 4. 신규 회원이면 서비스 토큰 없이 반환
        if (existingUser.isEmpty()) {
            return new KakaoLoginResponse(
                    false,
                    kakaoUser.properties().nickname(),
                    kakaoUser.properties().profileImage(),
                    kakaoUser.kakaoAccount().email(),
                    null
            );
        }

        // 5. 기존 회원이면 MOIVE Access/Refresh Token 발급
        User user = existingUser.get();

        String accessTokenJwt =
                jwtTokenProvider.createAccessToken(user.getId());

        String refreshToken =
                jwtTokenProvider.createRefreshToken(user.getId());

        // 6. Refresh Token 저장
        user.updateRefreshToken(
                refreshToken,
                LocalDateTime.now().plusDays(14)
        );

        // 7. 기존 회원 정보와 서비스 토큰 반환
        return new KakaoLoginResponse(
                true,
                kakaoUser.properties().nickname(),
                kakaoUser.properties().profileImage(),
                kakaoUser.kakaoAccount().email(),
                new TokenResponse(
                        accessTokenJwt,
                        refreshToken
                )
        );
    }

    private void validateKakaoUserInfo(KakaoUserInfoResponse kakaoUser) {

        if (kakaoUser == null
                || kakaoUser.id() == null
                || kakaoUser.properties() == null
                || kakaoUser.properties().nickname() == null
                || kakaoUser.properties().profileImage() == null
                || kakaoUser.kakaoAccount() == null
                || kakaoUser.kakaoAccount().email() == null) {

            throw new CustomException(
                    CustomErrorCode.KAKAO_REQUIRED_INFO_MISSING
            );
        }
    }

    private void validateRequiredAgreements(SignupRequest request) {

        if (request.agreements() == null) {
            throw new CustomException(
                    CustomErrorCode.REQUIRED_AGREEMENT_NOT_ACCEPTED
            );
        }

        boolean serviceAgreed = request.agreements()
                .stream()
                .anyMatch(agreement ->
                        agreement.type() == AgreementType.SERVICE
                                && agreement.agreed()
                );

        boolean privacyAgreed = request.agreements()
                .stream()
                .anyMatch(agreement ->
                        agreement.type() == AgreementType.PRIVACY
                                && agreement.agreed()
                );

        if (!serviceAgreed || !privacyAgreed) {
            throw new CustomException(
                    CustomErrorCode.REQUIRED_AGREEMENT_NOT_ACCEPTED
            );
        }
    }

    @Transactional
    public TokenResponse signup(SignupRequest request) {

        // 1. Kakao Access Token으로 사용자 정보 다시 조회
        KakaoUserInfoResponse kakaoUser =
                kakaoAuthService.getUserInfo(request.accessToken());

        // 2. 카카오 필수 사용자 정보 검증
        validateKakaoUserInfo(kakaoUser);

        // 3. 이미 가입된 활성 회원인지 확인
        boolean alreadyRegistered = userRepository
                .findByKakaoMemberIdAndStatus(
                        kakaoUser.id(),
                        UserStatus.ACTIVE
                )
                .isPresent();

        if (alreadyRegistered) {
            throw new CustomException(
                    CustomErrorCode.ALREADY_REGISTERED_USER
            );
        }

        // 4. 필수 약관 동의 검증
        validateRequiredAgreements(request);

        // 5. 신규 회원 생성
        User user = User.createKakaoUser(
                kakaoUser.id(),
                kakaoUser.kakaoAccount().email(),
                kakaoUser.properties().nickname(),
                kakaoUser.properties().profileImage()
        );

        User savedUser = userRepository.save(user);

        // 6. 약관 동의 내역 저장
        List<UserAgreement> agreements = request.agreements()
                .stream()
                .map(agreement -> UserAgreement.create(
                        user,
                        agreement.type(),
                        agreement.version(),
                        agreement.agreed()
                ))
                .toList();

        userAgreementRepository.saveAll(agreements);

        // 7. MOIVE Access/Refresh Token 발급
        String accessToken =
                jwtTokenProvider.createAccessToken(savedUser.getId());

        String refreshToken =
                jwtTokenProvider.createRefreshToken(savedUser.getId());

        // 8. Refresh Token 저장
        savedUser.updateRefreshToken(
                refreshToken,
                LocalDateTime.now().plusDays(14)
        );

        // 9. 서비스 토큰 반환
        return new TokenResponse(
                accessToken,
                refreshToken
        );


    }
    @Transactional
    public TokenResponse reissue(ReissueRequest request) {

        String refreshToken = request.refreshToken();

        // 1. Refresh Token 자체 유효성 검증
        if (!jwtTokenProvider.validateRefreshToken(refreshToken)) {
            throw new CustomException(
                    CustomErrorCode.INVALID_REFRESH_TOKEN
            );
        }

        // 2. Refresh Token에서 userId 추출
        Long userId = jwtTokenProvider.getUserId(refreshToken);

        // 3. User 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new CustomException(
                                CustomErrorCode.USER_NOT_FOUND
                        )
                );

        // 4. DB에 저장된 Refresh Token과 일치하는지 검증
        if (user.getRefreshToken() == null
                || !user.getRefreshToken().equals(refreshToken)) {

            throw new CustomException(
                    CustomErrorCode.INVALID_REFRESH_TOKEN
            );
        }

        // 5. 새로운 Access / Refresh Token 발급
        String newAccessToken =
                jwtTokenProvider.createAccessToken(userId);

        String newRefreshToken =
                jwtTokenProvider.createRefreshToken(userId);

        // 6. Refresh Token Rotation
        user.updateRefreshToken(
                newRefreshToken,
                LocalDateTime.now().plusDays(14)
        );

        // 7. 새 토큰 반환
        return new TokenResponse(
                newAccessToken,
                newRefreshToken
        );
    }

    @Transactional
    public void logout(LogoutRequest request) {

        String refreshToken = request.refreshToken();

        // 1. Refresh Token 유효성 검증
        if (!jwtTokenProvider.validateRefreshToken(refreshToken)) {
            throw new CustomException(
                    CustomErrorCode.INVALID_REFRESH_TOKEN
            );
        }

        // 2. Refresh Token에서 userId 추출
        Long userId = jwtTokenProvider.getUserId(refreshToken);

        // 3. 회원 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new CustomException(
                                CustomErrorCode.USER_NOT_FOUND
                        )
                );

        // 4. DB에 저장된 Refresh Token과 일치하는지 확인
        if (user.getRefreshToken() == null
                || !user.getRefreshToken().equals(refreshToken)) {

            throw new CustomException(
                    CustomErrorCode.INVALID_REFRESH_TOKEN
            );
        }

        // 5. DB에서 Refresh Token 제거
        user.clearRefreshToken();
    }
}
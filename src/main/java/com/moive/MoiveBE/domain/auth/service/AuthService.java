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

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final KakaoAuthService kakaoAuthService;
    private final UserRepository userRepository;
    private final UserAgreementRepository userAgreementRepository;

    public KakaoLoginResponse loginWithKakao(String accessToken) {

        // 1. Kakao Access Token으로 카카오 사용자 정보 조회
        KakaoUserInfoResponse kakaoUser =
                kakaoAuthService.getUserInfo(accessToken);

        // 2. 카카오 필수 사용자 정보 검증
        validateKakaoUserInfo(kakaoUser);

        // 3. Kakao Member ID로 기존 활성 회원 조회
        boolean registered = userRepository
                .findByKakaoMemberIdAndStatus(
                        kakaoUser.id(),
                        UserStatus.ACTIVE
                )
                .isPresent();

        // 4. 기존/신규 여부와 카카오 프로필 정보 반환
        return new KakaoLoginResponse(
                registered,
                kakaoUser.properties().nickname(),
                kakaoUser.properties().profileImage(),
                kakaoUser.kakaoAccount().email()
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
    public void signup(SignupRequest request) {

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

        userRepository.save(user);

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
    }
}
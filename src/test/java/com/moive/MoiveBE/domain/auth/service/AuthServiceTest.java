package com.moive.MoiveBE.domain.auth.service;

import com.moive.MoiveBE.domain.auth.dto.KakaoLoginResponse;
import com.moive.MoiveBE.domain.auth.dto.KakaoUserInfoResponse;
import com.moive.MoiveBE.domain.auth.dto.SignupRequest;
import com.moive.MoiveBE.domain.user.entity.AgreementType;
import com.moive.MoiveBE.domain.user.entity.User;
import com.moive.MoiveBE.domain.user.entity.UserAgreement;
import com.moive.MoiveBE.domain.user.entity.UserStatus;
import com.moive.MoiveBE.domain.user.repository.UserAgreementRepository;
import com.moive.MoiveBE.domain.user.repository.UserRepository;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private KakaoAuthService kakaoAuthService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserAgreementRepository userAgreementRepository;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                kakaoAuthService,
                userRepository,
                userAgreementRepository
        );
    }

    @Test
    void 신규_회원이면_registered_false를_반환한다() {
        // given
        String accessToken = "test-access-token";
        KakaoUserInfoResponse kakaoUser = mockKakaoUser();

        when(kakaoAuthService.getUserInfo(accessToken))
                .thenReturn(kakaoUser);

        when(userRepository.findByKakaoMemberIdAndStatus(
                12345L,
                UserStatus.ACTIVE
        )).thenReturn(Optional.empty());

        // when
        KakaoLoginResponse result =
                authService.loginWithKakao(accessToken);

        // then
        assertThat(result.registered()).isFalse();
        assertThat(result.nickname())
                .isEqualTo("테스트유저");
        assertThat(result.profileImageUrl())
                .isEqualTo("https://example.com/profile.jpg");
        assertThat(result.email())
                .isEqualTo("test@kakao.com");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void 기존_활성_회원이면_registered_true를_반환한다() {
        // given
        String accessToken = "test-access-token";
        KakaoUserInfoResponse kakaoUser = mockKakaoUser();

        when(kakaoAuthService.getUserInfo(accessToken))
                .thenReturn(kakaoUser);

        User existingUser = mock(User.class);

        when(userRepository.findByKakaoMemberIdAndStatus(
                12345L,
                UserStatus.ACTIVE
        )).thenReturn(Optional.of(existingUser));

        // when
        KakaoLoginResponse result =
                authService.loginWithKakao(accessToken);

        // then
        assertThat(result.registered()).isTrue();
        assertThat(result.email())
                .isEqualTo("test@kakao.com");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void 카카오_필수_정보가_없으면_예외가_발생한다() {
        // given
        String accessToken = "test-access-token";

        KakaoUserInfoResponse kakaoUser =
                mock(KakaoUserInfoResponse.class);

        when(kakaoAuthService.getUserInfo(accessToken))
                .thenReturn(kakaoUser);

        when(kakaoUser.id())
                .thenReturn(null);

        // when & then
        assertThatThrownBy(() ->
                authService.loginWithKakao(accessToken)
        )
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> {
                    CustomException customException =
                            (CustomException) exception;

                    assertThat(customException.getCustomErrorCode())
                            .isEqualTo(
                                    CustomErrorCode.KAKAO_REQUIRED_INFO_MISSING
                            );
                });

        verifyNoInteractions(userRepository);
    }

    @Test
    void 회원가입에_성공하면_회원과_약관동의가_저장된다() {
        // given
        String accessToken = "test-access-token";
        KakaoUserInfoResponse kakaoUser = mockKakaoUser();

        when(kakaoAuthService.getUserInfo(accessToken))
                .thenReturn(kakaoUser);

        when(userRepository.findByKakaoMemberIdAndStatus(
                12345L,
                UserStatus.ACTIVE
        )).thenReturn(Optional.empty());

        SignupRequest request = mockSignupRequest(
                accessToken,
                true,
                true,
                false
        );

        // when
        authService.signup(request);

        // then
        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getKakaoMemberId())
                .isEqualTo(12345L);
        assertThat(savedUser.getEmail())
                .isEqualTo("test@kakao.com");
        assertThat(savedUser.getNickname())
                .isEqualTo("테스트유저");
        assertThat(savedUser.getStatus())
                .isEqualTo(UserStatus.ACTIVE);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserAgreement>> agreementCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(userAgreementRepository)
                .saveAll(agreementCaptor.capture());

        List<UserAgreement> agreements =
                agreementCaptor.getValue();

        assertThat(agreements).hasSize(3);
    }

    @Test
    void 이미_가입된_회원이면_회원가입_예외가_발생한다() {
        // given
        String accessToken = "test-access-token";
        KakaoUserInfoResponse kakaoUser = mockKakaoUser();

        when(kakaoAuthService.getUserInfo(accessToken))
                .thenReturn(kakaoUser);

        when(userRepository.findByKakaoMemberIdAndStatus(
                12345L,
                UserStatus.ACTIVE
        )).thenReturn(Optional.of(mock(User.class)));

        SignupRequest request = mockSignupRequest(
                accessToken,
                true,
                true,
                false
        );

        // when & then
        assertThatThrownBy(() ->
                authService.signup(request)
        )
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> {
                    CustomException customException =
                            (CustomException) exception;

                    assertThat(customException.getCustomErrorCode())
                            .isEqualTo(
                                    CustomErrorCode.ALREADY_REGISTERED_USER
                            );
                });

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(userAgreementRepository);
    }

    @Test
    void 필수_약관에_동의하지_않으면_회원가입_예외가_발생한다() {
        // given
        String accessToken = "test-access-token";
        KakaoUserInfoResponse kakaoUser = mockKakaoUser();

        when(kakaoAuthService.getUserInfo(accessToken))
                .thenReturn(kakaoUser);

        when(userRepository.findByKakaoMemberIdAndStatus(
                12345L,
                UserStatus.ACTIVE
        )).thenReturn(Optional.empty());

        SignupRequest request = mockSignupRequest(
                accessToken,
                true,
                false,
                false
        );

        // when & then
        assertThatThrownBy(() ->
                authService.signup(request)
        )
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> {
                    CustomException customException =
                            (CustomException) exception;

                    assertThat(customException.getCustomErrorCode())
                            .isEqualTo(
                                    CustomErrorCode.REQUIRED_AGREEMENT_NOT_ACCEPTED
                            );
                });

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(userAgreementRepository);
    }

    private KakaoUserInfoResponse mockKakaoUser() {

        KakaoUserInfoResponse kakaoUser =
                mock(KakaoUserInfoResponse.class);

        KakaoUserInfoResponse.Properties properties =
                mock(KakaoUserInfoResponse.Properties.class);

        KakaoUserInfoResponse.KakaoAccount kakaoAccount =
                mock(KakaoUserInfoResponse.KakaoAccount.class);

        when(kakaoUser.id())
                .thenReturn(12345L);

        when(kakaoUser.properties())
                .thenReturn(properties);

        when(properties.nickname())
                .thenReturn("테스트유저");

        when(properties.profileImage())
                .thenReturn("https://example.com/profile.jpg");

        when(kakaoUser.kakaoAccount())
                .thenReturn(kakaoAccount);

        when(kakaoAccount.email())
                .thenReturn("test@kakao.com");

        return kakaoUser;
    }

    private SignupRequest mockSignupRequest(
            String accessToken,
            boolean serviceAgreed,
            boolean privacyAgreed,
            boolean marketingAgreed
    ) {

        SignupRequest request =
                mock(SignupRequest.class);

        SignupRequest.AgreementRequest service =
                mock(SignupRequest.AgreementRequest.class);

        SignupRequest.AgreementRequest privacy =
                mock(SignupRequest.AgreementRequest.class);

        SignupRequest.AgreementRequest marketing =
                mock(SignupRequest.AgreementRequest.class);

        lenient().when(request.accessToken())
                .thenReturn(accessToken);

        lenient().when(service.type())
                .thenReturn(AgreementType.SERVICE);

        lenient().when(service.version())
                .thenReturn("1.0");

        lenient().when(service.agreed())
                .thenReturn(serviceAgreed);

        lenient().when(privacy.type())
                .thenReturn(AgreementType.PRIVACY);

        lenient().when(privacy.version())
                .thenReturn("1.0");

        lenient().when(privacy.agreed())
                .thenReturn(privacyAgreed);

        lenient().when(marketing.type())
                .thenReturn(AgreementType.MARKETING);

        lenient().when(marketing.version())
                .thenReturn("1.0");

        lenient().when(marketing.agreed())
                .thenReturn(marketingAgreed);

        lenient().when(request.agreements())
                .thenReturn(
                        List.of(
                                service,
                                privacy,
                                marketing
                        )
                );

        return request;
    }
}
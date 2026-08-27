package com.moive.MoiveBE.domain.auth.service;

import com.moive.MoiveBE.domain.auth.dto.*;
import com.moive.MoiveBE.domain.user.entity.AgreementType;
import com.moive.MoiveBE.domain.user.entity.User;
import com.moive.MoiveBE.domain.user.entity.UserAgreement;
import com.moive.MoiveBE.domain.user.entity.UserStatus;
import com.moive.MoiveBE.domain.user.repository.UserAgreementRepository;
import com.moive.MoiveBE.domain.user.repository.UserRepository;
import com.moive.MoiveBE.domain.auth.dto.LogoutRequest;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import com.moive.MoiveBE.global.jwt.JwtTokenProvider;

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

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                kakaoAuthService,
                userRepository,
                userAgreementRepository,
                jwtTokenProvider
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

        // 신규 회원은 아직 MOIVE JWT를 발급하지 않음
        assertThat(result.token()).isNull();

        verifyNoInteractions(jwtTokenProvider);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void 기존_활성_회원이면_JWT를_발급한다() {
        // given
        String accessToken = "test-access-token";
        KakaoUserInfoResponse kakaoUser = mockKakaoUser();

        when(kakaoAuthService.getUserInfo(accessToken))
                .thenReturn(kakaoUser);

        User existingUser = mock(User.class);

        when(existingUser.getId())
                .thenReturn(1L);

        when(userRepository.findByKakaoMemberIdAndStatus(
                12345L,
                UserStatus.ACTIVE
        )).thenReturn(Optional.of(existingUser));

        when(jwtTokenProvider.createAccessToken(1L))
                .thenReturn("access-token");

        when(jwtTokenProvider.createRefreshToken(1L))
                .thenReturn("refresh-token");

        // when
        KakaoLoginResponse result =
                authService.loginWithKakao(accessToken);

        // then
        assertThat(result.registered()).isTrue();
        assertThat(result.email())
                .isEqualTo("test@kakao.com");

        assertThat(result.token()).isNotNull();
        assertThat(result.token().accessToken())
                .isEqualTo("access-token");
        assertThat(result.token().refreshToken())
                .isEqualTo("refresh-token");

        verify(jwtTokenProvider)
                .createAccessToken(1L);

        verify(jwtTokenProvider)
                .createRefreshToken(1L);

        verify(existingUser)
                .updateRefreshToken(
                        eq("refresh-token"),
                        any()
                );

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
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void 회원가입에_성공하면_회원과_약관동의를_저장하고_JWT를_발급한다() {
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

        /*
         * Repository를 Mock으로 사용하기 때문에 실제 JPA처럼
         * 저장 후 ID가 자동 생성되지 않는다.
         *
         * 따라서 save()가 호출되면 테스트용 User를 반환하도록 설정한다.
         */
        User savedUser = mock(User.class);

        when(savedUser.getId())
                .thenReturn(1L);

        when(userRepository.save(any(User.class)))
                .thenReturn(savedUser);

        when(jwtTokenProvider.createAccessToken(1L))
                .thenReturn("access-token");

        when(jwtTokenProvider.createRefreshToken(1L))
                .thenReturn("refresh-token");

        // when
        TokenResponse result =
                authService.signup(request);

        // then
        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository)
                .save(userCaptor.capture());

        User createdUser = userCaptor.getValue();

        assertThat(createdUser.getKakaoMemberId())
                .isEqualTo(12345L);
        assertThat(createdUser.getEmail())
                .isEqualTo("test@kakao.com");
        assertThat(createdUser.getNickname())
                .isEqualTo("테스트유저");
        assertThat(createdUser.getStatus())
                .isEqualTo(UserStatus.ACTIVE);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserAgreement>> agreementCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(userAgreementRepository)
                .saveAll(agreementCaptor.capture());

        List<UserAgreement> agreements =
                agreementCaptor.getValue();

        assertThat(agreements).hasSize(3);

        assertThat(result.accessToken())
                .isEqualTo("access-token");

        assertThat(result.refreshToken())
                .isEqualTo("refresh-token");

        verify(jwtTokenProvider)
                .createAccessToken(1L);

        verify(jwtTokenProvider)
                .createRefreshToken(1L);

        verify(savedUser)
                .updateRefreshToken(
                        eq("refresh-token"),
                        any()
                );
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
        verifyNoInteractions(jwtTokenProvider);
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
        verifyNoInteractions(jwtTokenProvider);
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

    @Test
    void 유효한_Refresh_Token이면_새로운_토큰을_발급한다() {
        // given
        String refreshToken = "old-refresh-token";
        Long userId = 1L;

        ReissueRequest request =
                new ReissueRequest(refreshToken);

        User user = mock(User.class);

        when(jwtTokenProvider.validateToken(refreshToken))
                .thenReturn(true);

        when(jwtTokenProvider.getUserId(refreshToken))
                .thenReturn(userId);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        when(user.getRefreshToken())
                .thenReturn(refreshToken);

        when(jwtTokenProvider.createAccessToken(userId))
                .thenReturn("new-access-token");

        when(jwtTokenProvider.createRefreshToken(userId))
                .thenReturn("new-refresh-token");

        // when
        TokenResponse result =
                authService.reissue(request);

        // then
        assertThat(result.accessToken())
                .isEqualTo("new-access-token");

        assertThat(result.refreshToken())
                .isEqualTo("new-refresh-token");

        verify(user).updateRefreshToken(
                eq("new-refresh-token"),
                any()
        );
    }

    @Test
    void 유효하지_않은_Refresh_Token이면_예외가_발생한다() {
        // given
        String refreshToken = "invalid-refresh-token";

        ReissueRequest request =
                new ReissueRequest(refreshToken);

        when(jwtTokenProvider.validateToken(refreshToken))
                .thenReturn(false);

        // when & then
        assertThatThrownBy(() ->
                authService.reissue(request)
        )
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> {
                    CustomException customException =
                            (CustomException) exception;

                    assertThat(customException.getCustomErrorCode())
                            .isEqualTo(
                                    CustomErrorCode.INVALID_REFRESH_TOKEN
                            );
                });

        verifyNoInteractions(userRepository);
    }

    @Test
    void DB의_Refresh_Token과_일치하지_않으면_예외가_발생한다() {
        // given
        String refreshToken = "request-refresh-token";
        Long userId = 1L;

        ReissueRequest request =
                new ReissueRequest(refreshToken);

        User user = mock(User.class);

        when(jwtTokenProvider.validateToken(refreshToken))
                .thenReturn(true);

        when(jwtTokenProvider.getUserId(refreshToken))
                .thenReturn(userId);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        when(user.getRefreshToken())
                .thenReturn("different-refresh-token");

        // when & then
        assertThatThrownBy(() ->
                authService.reissue(request)
        )
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> {
                    CustomException customException =
                            (CustomException) exception;

                    assertThat(customException.getCustomErrorCode())
                            .isEqualTo(
                                    CustomErrorCode.INVALID_REFRESH_TOKEN
                            );
                });

        verify(jwtTokenProvider, never())
                .createAccessToken(anyLong());

        verify(jwtTokenProvider, never())
                .createRefreshToken(anyLong());
    }

    @Test
    void 로그아웃에_성공하면_Refresh_Token을_삭제한다() {
        // given
        String refreshToken = "refresh-token";
        Long userId = 1L;

        LogoutRequest request =
                new LogoutRequest(refreshToken);

        User user = mock(User.class);

        when(jwtTokenProvider.validateToken(refreshToken))
                .thenReturn(true);

        when(jwtTokenProvider.getUserId(refreshToken))
                .thenReturn(userId);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        when(user.getRefreshToken())
                .thenReturn(refreshToken);

        // when
        authService.logout(request);

        // then
        verify(user).clearRefreshToken();
    }

    @Test
    void 유효하지_않은_Refresh_Token으로_로그아웃하면_예외가_발생한다() {
        // given
        String refreshToken = "invalid-refresh-token";

        LogoutRequest request =
                new LogoutRequest(refreshToken);

        when(jwtTokenProvider.validateToken(refreshToken))
                .thenReturn(false);

        // when & then
        assertThatThrownBy(() ->
                authService.logout(request)
        )
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> {
                    CustomException customException =
                            (CustomException) exception;

                    assertThat(customException.getCustomErrorCode())
                            .isEqualTo(
                                    CustomErrorCode.INVALID_REFRESH_TOKEN
                            );
                });

        verifyNoInteractions(userRepository);
    }

    @Test
    void DB의_Refresh_Token과_다르면_로그아웃_예외가_발생한다() {
        // given
        String refreshToken = "request-refresh-token";
        Long userId = 1L;

        LogoutRequest request =
                new LogoutRequest(refreshToken);

        User user = mock(User.class);

        when(jwtTokenProvider.validateToken(refreshToken))
                .thenReturn(true);

        when(jwtTokenProvider.getUserId(refreshToken))
                .thenReturn(userId);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        when(user.getRefreshToken())
                .thenReturn("different-refresh-token");

        // when & then
        assertThatThrownBy(() ->
                authService.logout(request)
        )
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> {
                    CustomException customException =
                            (CustomException) exception;

                    assertThat(customException.getCustomErrorCode())
                            .isEqualTo(
                                    CustomErrorCode.INVALID_REFRESH_TOKEN
                            );
                });

        verify(user, never()).clearRefreshToken();
    }
}
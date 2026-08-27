package com.moive.MoiveBE.domain.auth.service;

import com.moive.MoiveBE.domain.auth.dto.KakaoLoginResponse;
import com.moive.MoiveBE.domain.auth.dto.KakaoUserInfoResponse;
import com.moive.MoiveBE.domain.user.entity.User;
import com.moive.MoiveBE.domain.user.entity.UserStatus;
import com.moive.MoiveBE.domain.user.repository.UserAgreementRepository;
import com.moive.MoiveBE.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        KakaoUserInfoResponse kakaoUser = mock(KakaoUserInfoResponse.class);
        KakaoUserInfoResponse.Properties properties =
                mock(KakaoUserInfoResponse.Properties.class);

        when(kakaoUser.getId()).thenReturn(12345L);
        when(kakaoUser.getProperties()).thenReturn(properties);
        when(properties.getNickname()).thenReturn("테스트유저");
        when(properties.getProfileImage())
                .thenReturn("https://example.com/profile.jpg");

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
        assertThat(result.isRegistered()).isFalse();
        assertThat(result.getNickname()).isEqualTo("테스트유저");
        assertThat(result.getProfileImageUrl())
                .isEqualTo("https://example.com/profile.jpg");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void 기존_활성_회원이면_registered_true를_반환한다() {
        // given
        String accessToken = "test-access-token";

        KakaoUserInfoResponse kakaoUser = mock(KakaoUserInfoResponse.class);
        KakaoUserInfoResponse.Properties properties =
                mock(KakaoUserInfoResponse.Properties.class);

        when(kakaoUser.getId()).thenReturn(12345L);
        when(kakaoUser.getProperties()).thenReturn(properties);
        when(properties.getNickname()).thenReturn("테스트유저");
        when(properties.getProfileImage())
                .thenReturn("https://example.com/profile.jpg");

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
        assertThat(result.isRegistered()).isTrue();

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

        when(kakaoUser.getId())
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
}
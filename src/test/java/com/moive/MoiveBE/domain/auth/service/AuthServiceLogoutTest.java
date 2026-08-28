package com.moive.MoiveBE.domain.auth.service;

import com.moive.MoiveBE.domain.auth.dto.LogoutRequest;
import com.moive.MoiveBE.domain.user.entity.User;
import com.moive.MoiveBE.domain.user.repository.UserAgreementRepository;
import com.moive.MoiveBE.domain.user.repository.UserRepository;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import com.moive.MoiveBE.global.jwt.JwtTokenProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceLogoutTest {

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
    void 로그아웃에_성공하면_Refresh_Token을_삭제한다() {
        // given
        String refreshToken = "refresh-token";
        Long userId = 1L;

        LogoutRequest request =
                new LogoutRequest(refreshToken);

        User user = mock(User.class);

        when(jwtTokenProvider.validateRefreshToken(refreshToken))
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

        when(jwtTokenProvider.validateRefreshToken(refreshToken))
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

        when(jwtTokenProvider.validateRefreshToken(refreshToken))
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

    @Test
    void Access_Token으로_로그아웃하면_예외가_발생한다() {
        // given
        String accessToken = "access-token";

        LogoutRequest request =
                new LogoutRequest(accessToken);

        when(jwtTokenProvider.validateRefreshToken(accessToken))
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

        verify(jwtTokenProvider, never())
                .getUserId(anyString());
    }
}
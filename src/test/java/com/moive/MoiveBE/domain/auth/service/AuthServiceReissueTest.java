package com.moive.MoiveBE.domain.auth.service;

import com.moive.MoiveBE.domain.auth.dto.ReissueRequest;
import com.moive.MoiveBE.domain.auth.dto.TokenResponse;
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
class AuthServiceReissueTest {

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
    void 유효한_Refresh_Token이면_새로운_토큰을_발급한다() {
        // given
        String refreshToken = "old-refresh-token";
        Long userId = 1L;

        ReissueRequest request =
                new ReissueRequest(refreshToken);

        User user = mock(User.class);

        when(jwtTokenProvider.validateRefreshToken(refreshToken))
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

        when(jwtTokenProvider.validateRefreshToken(refreshToken))
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
    void Access_Token으로_재발급하면_예외가_발생한다() {
        // given
        String accessToken = "access-token";

        ReissueRequest request =
                new ReissueRequest(accessToken);

        when(jwtTokenProvider.validateRefreshToken(accessToken))
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

        verify(jwtTokenProvider, never())
                .getUserId(anyString());
    }
}
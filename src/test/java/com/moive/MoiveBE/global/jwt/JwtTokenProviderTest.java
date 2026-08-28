package com.moive.MoiveBE.global.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        String secret = "12345678901234567890123456789012";

        jwtTokenProvider = new JwtTokenProvider(
                secret,
                3600000,
                1209600000
        );
    }

    @Test
    void Access_Token을_생성할_수_있다() {
        // given
        Long userId = 1L;

        // when
        String accessToken =
                jwtTokenProvider.createAccessToken(userId);

        // then
        assertThat(accessToken).isNotBlank();
    }

    @Test
    void Refresh_Token을_생성할_수_있다() {
        // given
        Long userId = 1L;

        // when
        String refreshToken =
                jwtTokenProvider.createRefreshToken(userId);

        // then
        assertThat(refreshToken).isNotBlank();
    }

    @Test
    void JWT에서_userId를_추출할_수_있다() {
        // given
        Long userId = 1L;

        String accessToken =
                jwtTokenProvider.createAccessToken(userId);

        // when
        Long extractedUserId =
                jwtTokenProvider.getUserId(accessToken);

        // then
        assertThat(extractedUserId)
                .isEqualTo(userId);
    }

    @Test
    void Access_Token은_Access_Token으로_검증된다() {
        // given
        String accessToken =
                jwtTokenProvider.createAccessToken(1L);

        // when
        boolean result =
                jwtTokenProvider.validateAccessToken(accessToken);

        // then
        assertThat(result).isTrue();
    }

    @Test
    void Refresh_Token은_Refresh_Token으로_검증된다() {
        // given
        String refreshToken =
                jwtTokenProvider.createRefreshToken(1L);

        // when
        boolean result =
                jwtTokenProvider.validateRefreshToken(refreshToken);

        // then
        assertThat(result).isTrue();
    }

    @Test
    void Refresh_Token은_Access_Token으로_검증되지_않는다() {
        // given
        String refreshToken =
                jwtTokenProvider.createRefreshToken(1L);

        // when
        boolean result =
                jwtTokenProvider.validateAccessToken(refreshToken);

        // then
        assertThat(result).isFalse();
    }

    @Test
    void Access_Token은_Refresh_Token으로_검증되지_않는다() {
        // given
        String accessToken =
                jwtTokenProvider.createAccessToken(1L);

        // when
        boolean result =
                jwtTokenProvider.validateRefreshToken(accessToken);

        // then
        assertThat(result).isFalse();
    }

    @Test
    void 잘못된_JWT는_Access_Token으로_검증되지_않는다() {
        // given
        String invalidToken = "invalid-token";

        // when
        boolean result =
                jwtTokenProvider.validateAccessToken(invalidToken);

        // then
        assertThat(result).isFalse();
    }

    @Test
    void 잘못된_JWT는_Refresh_Token으로_검증되지_않는다() {
        // given
        String invalidToken = "invalid-token";

        // when
        boolean result =
                jwtTokenProvider.validateRefreshToken(invalidToken);

        // then
        assertThat(result).isFalse();
    }
}
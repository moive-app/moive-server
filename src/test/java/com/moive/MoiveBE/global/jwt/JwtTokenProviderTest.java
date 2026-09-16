package com.moive.MoiveBE.global.jwt;

import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        // when & then
        assertThatCode(() ->
                jwtTokenProvider.validateAccessToken(accessToken)
        ).doesNotThrowAnyException();
    }

    @Test
    void Refresh_Token은_Refresh_Token으로_검증된다() {
        // given
        String refreshToken =
                jwtTokenProvider.createRefreshToken(1L);

        // when & then
        assertThatCode(() ->
                jwtTokenProvider.validateRefreshToken(refreshToken)
        ).doesNotThrowAnyException();
    }

    @Test
    void Refresh_Token은_Access_Token으로_검증되지_않는다() {
        // given
        String refreshToken =
                jwtTokenProvider.createRefreshToken(1L);

        // when & then
        assertThatThrownBy(() ->
                jwtTokenProvider.validateAccessToken(refreshToken)
        )
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> {
                    CustomException customException =
                            (CustomException) exception;

                    assertThat(customException.getCustomErrorCode())
                            .isEqualTo(CustomErrorCode.INVALID_ACCESS_TOKEN);
                });
    }

    @Test
    void Access_Token은_Refresh_Token으로_검증되지_않는다() {
        // given
        String accessToken =
                jwtTokenProvider.createAccessToken(1L);

        // when & then
        assertThatThrownBy(() ->
                jwtTokenProvider.validateRefreshToken(accessToken)
        )
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> {
                    CustomException customException =
                            (CustomException) exception;

                    assertThat(customException.getCustomErrorCode())
                            .isEqualTo(CustomErrorCode.INVALID_REFRESH_TOKEN);
                });
    }

    @Test
    void 잘못된_JWT는_Access_Token으로_검증되지_않는다() {
        // given
        String invalidToken = "invalid-token";

        // when & then
        assertThatThrownBy(() ->
                jwtTokenProvider.validateAccessToken(invalidToken)
        )
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> {
                    CustomException customException =
                            (CustomException) exception;

                    assertThat(customException.getCustomErrorCode())
                            .isEqualTo(CustomErrorCode.INVALID_ACCESS_TOKEN);
                });
    }

    @Test
    void 잘못된_JWT는_Refresh_Token으로_검증되지_않는다() {
        // given
        String invalidToken = "invalid-token";

        // when & then
        assertThatThrownBy(() ->
                jwtTokenProvider.validateRefreshToken(invalidToken)
        )
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> {
                    CustomException customException =
                            (CustomException) exception;

                    assertThat(customException.getCustomErrorCode())
                            .isEqualTo(CustomErrorCode.INVALID_REFRESH_TOKEN);
                });
    }

    @Test
    void 만료된_Access_Token은_ACCESS_TOKEN_EXPIRED_예외가_발생한다() {
        // given
        JwtTokenProvider expiredTokenProvider =
                new JwtTokenProvider(
                        "12345678901234567890123456789012",
                        -1000,
                        1209600000
                );

        String expiredAccessToken =
                expiredTokenProvider.createAccessToken(1L);

        // when & then
        assertThatThrownBy(() ->
                expiredTokenProvider.validateAccessToken(expiredAccessToken)
        )
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> {
                    CustomException customException =
                            (CustomException) exception;

                    assertThat(customException.getCustomErrorCode())
                            .isEqualTo(CustomErrorCode.ACCESS_TOKEN_EXPIRED);
                });
    }

    @Test
    void 만료된_Refresh_Token은_REFRESH_TOKEN_EXPIRED_예외가_발생한다() {
        // given
        JwtTokenProvider expiredTokenProvider =
                new JwtTokenProvider(
                        "12345678901234567890123456789012",
                        3600000,
                        -1000
                );

        String expiredRefreshToken =
                expiredTokenProvider.createRefreshToken(1L);

        // when & then
        assertThatThrownBy(() ->
                expiredTokenProvider.validateRefreshToken(expiredRefreshToken)
        )
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> {
                    CustomException customException =
                            (CustomException) exception;

                    assertThat(customException.getCustomErrorCode())
                            .isEqualTo(CustomErrorCode.REFRESH_TOKEN_EXPIRED);
                });
    }

}
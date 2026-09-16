package com.moive.MoiveBE.global.jwt;

import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private static final String TOKEN_TYPE = "tokenType";
    private static final String ACCESS = "access";
    private static final String REFRESH = "refresh";

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration
    ) {
        this.secretKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );

        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    public String createAccessToken(Long userId) {
        return createToken(userId, accessTokenExpiration, ACCESS);
    }

    public String createRefreshToken(Long userId) {
        return createToken(userId, refreshTokenExpiration, REFRESH);
    }

    private String createToken(
            Long userId,
            long expiration,
            String tokenType
    ) {
        Date now = new Date();
        Date expiredAt = new Date(
                now.getTime() + expiration
        );

        return Jwts.builder()
                .subject(userId.toString())
                .claim(TOKEN_TYPE, tokenType)
                .issuedAt(now)
                .expiration(expiredAt)
                .signWith(secretKey)
                .compact();
    }

    public Long getUserId(String token) {

        Claims claims = getClaims(token);

        return Long.valueOf(
                claims.getSubject()
        );
    }

    public void validateAccessToken(String token) {
        validateTokenType(
                token,
                ACCESS,
                CustomErrorCode.ACCESS_TOKEN_EXPIRED,
                CustomErrorCode.INVALID_ACCESS_TOKEN
        );
    }

    public void validateRefreshToken(String token) {
        validateTokenType(
                token,
                REFRESH,
                CustomErrorCode.REFRESH_TOKEN_EXPIRED,
                CustomErrorCode.INVALID_REFRESH_TOKEN
        );
    }

    private void validateTokenType(
            String token,
            String expectedType,
            CustomErrorCode expiredErrorCode,
            CustomErrorCode invalidErrorCode
    ) {
        try {
            Claims claims = getClaims(token);

            String tokenType =
                    claims.get(TOKEN_TYPE, String.class);

            if (!expectedType.equals(tokenType)) {
                throw new CustomException(invalidErrorCode);
            }

        } catch (ExpiredJwtException e) {
            throw new CustomException(expiredErrorCode);

        } catch (CustomException e) {
            throw e;

        } catch (JwtException | IllegalArgumentException e) {
            throw new CustomException(invalidErrorCode);
        }
    }

    private Claims getClaims(String token) {

        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
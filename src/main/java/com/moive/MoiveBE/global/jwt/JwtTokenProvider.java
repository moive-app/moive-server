package com.moive.MoiveBE.global.jwt;

import io.jsonwebtoken.Claims;
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

    public boolean validateAccessToken(String token) {
        return validateTokenType(
                token,
                ACCESS
        );
    }

    public boolean validateRefreshToken(String token) {
        return validateTokenType(
                token,
                REFRESH
        );
    }

    private boolean validateTokenType(
            String token,
            String expectedType
    ) {
        try {
            Claims claims = getClaims(token);

            String tokenType =
                    claims.get(TOKEN_TYPE, String.class);

            return expectedType.equals(tokenType);

        } catch (Exception e) {
            return false;
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
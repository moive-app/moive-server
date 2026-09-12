package com.moive.MoiveBE.global.jwt;

import com.moive.MoiveBE.global.exception.CustomException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Qualifier("handlerExceptionResolver")
    private final HandlerExceptionResolver handlerExceptionResolver;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            String authorizationHeader =
                    request.getHeader("Authorization");

            if (authorizationHeader != null
                    && authorizationHeader.startsWith("Bearer ")) {

                String token =
                        authorizationHeader.substring(7);

                jwtTokenProvider.validateAccessToken(token);

                Long userId =
                        jwtTokenProvider.getUserId(token);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userId,
                                null,
                                List.of(
                                        new SimpleGrantedAuthority("ROLE_USER")
                                )
                        );

                SecurityContextHolder.getContext()
                        .setAuthentication(authentication);
            }

            filterChain.doFilter(request, response);

        } catch (CustomException e) {
            SecurityContextHolder.clearContext();

            handlerExceptionResolver.resolveException(
                    request,
                    response,
                    null,
                    e
            );

            return;
        }
    }
}
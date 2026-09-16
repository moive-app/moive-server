package com.moive.MoiveBE.global.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import org.springframework.web.servlet.HandlerExceptionResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private HandlerExceptionResolver handlerExceptionResolver;

    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        jwtAuthenticationFilter =
                new JwtAuthenticationFilter(
                        jwtTokenProvider,
                        handlerExceptionResolver
                );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void Authorization_헤더가_없으면_인증정보를_생성하지_않는다()
            throws Exception {

        // given
        when(request.getHeader("Authorization"))
                .thenReturn(null);

        // when
        jwtAuthenticationFilter.doFilterInternal(
                request,
                response,
                filterChain
        );

        // then
        Authentication authentication =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        assertThat(authentication).isNull();

        verifyNoInteractions(jwtTokenProvider);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void 유효한_Bearer_Token이면_인증정보를_생성한다()
            throws Exception {

        // given
        String token = "valid-access-token";

        when(request.getHeader("Authorization"))
                .thenReturn("Bearer " + token);

        when(jwtTokenProvider.getUserId(token))
                .thenReturn(1L);

        // when
        jwtAuthenticationFilter.doFilterInternal(
                request,
                response,
                filterChain
        );

        // then
        Authentication authentication =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal())
                .isEqualTo(1L);

        assertThat(authentication.isAuthenticated())
                .isTrue();

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void 유효하지_않은_Token이면_인증정보를_생성하지_않는다()
            throws Exception {

        // given
        String token = "invalid-access-token";

        when(request.getHeader("Authorization"))
                .thenReturn("Bearer " + token);

        CustomException exception =
                new CustomException(
                        CustomErrorCode.INVALID_ACCESS_TOKEN
                );

        doThrow(exception)
                .when(jwtTokenProvider)
                .validateAccessToken(token);

        // when
        jwtAuthenticationFilter.doFilterInternal(
                request,
                response,
                filterChain
        );

        // then
        Authentication authentication =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        assertThat(authentication).isNull();

        verify(jwtTokenProvider, never())
                .getUserId(anyString());

        verify(handlerExceptionResolver)
                .resolveException(
                        request,
                        response,
                        null,
                        exception
                );

        verify(filterChain, never())
                .doFilter(request, response);
    }

    @Test
    void Refresh_Token은_인증정보를_생성하지_않는다()
            throws Exception {

        // given
        String refreshToken = "refresh-token";

        when(request.getHeader("Authorization"))
                .thenReturn("Bearer " + refreshToken);

        CustomException exception =
                new CustomException(
                        CustomErrorCode.INVALID_ACCESS_TOKEN
                );

        doThrow(exception)
                .when(jwtTokenProvider)
                .validateAccessToken(refreshToken);

        // when
        jwtAuthenticationFilter.doFilterInternal(
                request,
                response,
                filterChain
        );

        // then
        Authentication authentication =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        assertThat(authentication).isNull();

        verify(jwtTokenProvider, never())
                .getUserId(anyString());

        verify(handlerExceptionResolver)
                .resolveException(
                        request,
                        response,
                        null,
                        exception
                );

        verify(filterChain, never())
                .doFilter(request, response);
    }

    @Test
    void 만료된_Access_Token은_ACCESS_TOKEN_EXPIRED로_처리된다()
            throws Exception {

        // given
        String token = "expired-access-token";

        when(request.getHeader("Authorization"))
                .thenReturn("Bearer " + token);

        CustomException exception =
                new CustomException(
                        CustomErrorCode.ACCESS_TOKEN_EXPIRED
                );

        doThrow(exception)
                .when(jwtTokenProvider)
                .validateAccessToken(token);

        // when
        jwtAuthenticationFilter.doFilterInternal(
                request,
                response,
                filterChain
        );

        // then
        verify(handlerExceptionResolver)
                .resolveException(
                        request,
                        response,
                        null,
                        exception
                );

        verify(filterChain, never())
                .doFilter(request, response);
    }

}
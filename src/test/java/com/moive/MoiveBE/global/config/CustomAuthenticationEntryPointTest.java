package com.moive.MoiveBE.global.config;

import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.servlet.HandlerExceptionResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CustomAuthenticationEntryPointTest {

    @Mock
    private HandlerExceptionResolver handlerExceptionResolver;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private AuthenticationException authenticationException;

    @Test
    void 인증_토큰이_없으면_TOKEN_MISSING_예외를_위임한다()
            throws Exception {

        // given
        CustomAuthenticationEntryPoint entryPoint =
                new CustomAuthenticationEntryPoint(
                        handlerExceptionResolver
                );

        // when
        entryPoint.commence(
                request,
                response,
                authenticationException
        );

        // then
        ArgumentCaptor<Exception> exceptionCaptor =
                ArgumentCaptor.forClass(Exception.class);

        verify(handlerExceptionResolver)
                .resolveException(
                        eq(request),
                        eq(response),
                        isNull(),
                        exceptionCaptor.capture()
                );

        CustomException exception =
                (CustomException) exceptionCaptor.getValue();

        assertThat(exception.getCustomErrorCode())
                .isEqualTo(CustomErrorCode.TOKEN_MISSING);
    }
}
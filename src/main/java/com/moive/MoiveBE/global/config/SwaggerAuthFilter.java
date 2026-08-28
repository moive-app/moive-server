package com.moive.MoiveBE.global.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Base64;

/**
 * prod 환경에서만 활성화
 */

@Component
@Profile("prod")
public class SwaggerAuthFilter implements Filter {

    @Value("${swagger.auth.username}")
    private String username;

    @Value("${swagger.auth.password}")
    private String password;

    // springdoc 기본 경로들
    private static final String[] PROTECTED_PATHS = {
            "/swagger-ui",
            "/v3/api-docs"
    };

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        if (isProtectedPath(request.getRequestURI())) {
            String authHeader = request.getHeader("Authorization");

            if (authHeader == null || !isValid(authHeader)) {
                response.setHeader("WWW-Authenticate", "Basic realm=\"Swagger\"");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }

        chain.doFilter(req, res);
    }

    private boolean isProtectedPath(String uri) {
        for (String path : PROTECTED_PATHS) {
            if (uri.startsWith(path)) {
                return true;
            }
        }
        return false;
    }

    private boolean isValid(String authHeader) {
        if (!authHeader.startsWith("Basic ")) {
            return false;
        }
        try {
            String base64Credentials = authHeader.substring("Basic ".length());
            String credentials = new String(Base64.getDecoder().decode(base64Credentials));
            String[] values = credentials.split(":", 2);
            return values.length == 2
                    && username.equals(values[0])
                    && password.equals(values[1]);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
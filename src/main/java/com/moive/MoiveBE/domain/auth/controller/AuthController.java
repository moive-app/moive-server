package com.moive.MoiveBE.domain.auth.controller;

import com.moive.MoiveBE.domain.auth.dto.KakaoLoginRequest;
import com.moive.MoiveBE.domain.auth.dto.KakaoLoginResponse;
import com.moive.MoiveBE.domain.auth.dto.SignupRequest;
import com.moive.MoiveBE.domain.auth.service.AuthService;
import com.moive.MoiveBE.global.common.BaseResponse;
import com.moive.MoiveBE.domain.auth.dto.TokenResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "인증 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "카카오 로그인",
            description = "카카오 Access Token으로 사용자를 인증하고 MOIVE 기존/신규 회원 여부를 확인합니다."
    )
    @PostMapping("/kakao")
    public BaseResponse<KakaoLoginResponse> kakaoLogin(
            @Valid @RequestBody KakaoLoginRequest request
    ) {
        return BaseResponse.success(
                authService.loginWithKakao(request.accessToken())
        );
    }

    @Operation(
            summary = "회원가입",
            description = "카카오 Access Token을 검증하고 약관 동의 후 신규 회원을 생성합니다."
    )
    @PostMapping("/signup")
    public BaseResponse<TokenResponse> signup(
            @Valid @RequestBody SignupRequest request
    ) {
        return BaseResponse.success(
                authService.signup(request)
        );
    }
}
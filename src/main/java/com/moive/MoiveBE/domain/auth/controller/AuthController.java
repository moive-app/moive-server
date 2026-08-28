package com.moive.MoiveBE.domain.auth.controller;

import com.moive.MoiveBE.domain.auth.dto.*;
import com.moive.MoiveBE.domain.auth.service.AuthService;
import com.moive.MoiveBE.domain.auth.dto.LogoutRequest;
import com.moive.MoiveBE.global.common.BaseResponse;

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

    @Operation(
            summary = "토큰 재발급",
            description = "Refresh Token을 검증하고 새로운 Access Token과 Refresh Token을 발급합니다."
    )
    @PostMapping("/reissue")
    public BaseResponse<TokenResponse> reissue(
            @Valid @RequestBody ReissueRequest request
    ) {
        return BaseResponse.success(
                authService.reissue(request)
        );
    }

    @Operation(
            summary = "로그아웃",
            description = "Refresh Token을 검증한 후 서버에 저장된 Refresh Token을 삭제합니다."
    )
    @PostMapping("/logout")
    public BaseResponse<Void> logout(
            @Valid @RequestBody LogoutRequest request
    ) {
        authService.logout(request);

        return BaseResponse.success(null);
    }
}
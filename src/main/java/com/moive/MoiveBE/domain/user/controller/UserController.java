package com.moive.MoiveBE.domain.user.controller;

import com.moive.MoiveBE.domain.user.dto.MyInfoResponse;
import com.moive.MoiveBE.domain.user.service.UserService;
import com.moive.MoiveBE.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "User", description = "회원 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @Operation(
            summary = "내 정보 조회",
            description = "현재 로그인한 사용자의 정보를 조회합니다."
    )
    @GetMapping("/me")
    public BaseResponse<MyInfoResponse> getMyInfo(
            @AuthenticationPrincipal Long userId
    ) {
        return BaseResponse.success(
                userService.getMyInfo(userId)
        );
    }

    @Operation(
            summary = "프로필 수정",
            description = "현재 로그인한 사용자의 닉네임과 프로필 이미지를 수정합니다."
    )
    @PatchMapping(
            value = "/me",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public BaseResponse<MyInfoResponse> updateMyProfile(
            @AuthenticationPrincipal Long userId,
            @RequestPart("nickname") String nickname,
            @RequestPart(value = "profileImage", required = false) MultipartFile profileImage
    ) {
        return BaseResponse.success(
                userService.updateMyProfile(
                        userId,
                        nickname,
                        profileImage
                )
        );
    }
}
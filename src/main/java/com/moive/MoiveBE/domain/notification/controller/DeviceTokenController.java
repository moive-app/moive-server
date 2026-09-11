package com.moive.MoiveBE.domain.notification.controller;

import com.moive.MoiveBE.domain.notification.dto.RegisterDeviceTokenRequest;
import com.moive.MoiveBE.domain.notification.service.DeviceTokenService;
import com.moive.MoiveBE.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Device", description = "기기 토큰 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/devices")
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    @Operation(summary = "FCM 푸시 토큰 등록/갱신", description = "앱 실행 시 호출. 동일 deviceId면 토큰을 갱신합니다.")
    @PutMapping("/token")
    public BaseResponse<Void> registerToken(@RequestBody RegisterDeviceTokenRequest request) {
        deviceTokenService.registerToken(request);
        return BaseResponse.success("기기 토큰이 등록되었습니다.", null);
    }

    @Operation(summary = "기기 토큰 해제", description = "로그아웃 시 해당 기기를 푸시 발송 대상에서 제외합니다.")
    @DeleteMapping("/token")
    public BaseResponse<Void> deleteToken(@RequestParam String deviceId) {
        deviceTokenService.deleteToken(deviceId);
        return BaseResponse.success("기기 토큰이 해제되었습니다.", null);
    }
}

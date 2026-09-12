package com.moive.MoiveBE.domain.notification.service;

import com.moive.MoiveBE.domain.notification.dto.RegisterDeviceTokenRequest;
import com.moive.MoiveBE.domain.notification.entity.DeviceToken;
import com.moive.MoiveBE.domain.notification.repository.DeviceTokenRepository;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;

    public void registerToken(RegisterDeviceTokenRequest request) {
        if (request.fcmToken() == null || request.fcmToken().isBlank() ||
            request.deviceId() == null || request.deviceId().isBlank()) {
            throw new CustomException(CustomErrorCode.DEVICE_TOKEN_MISSING);
        }

        Long userId = getCurrentUserId();
        Optional<DeviceToken> existing = deviceTokenRepository.findByDeviceId(request.deviceId());

        if (existing.isPresent()) {
            existing.get().updateFcmToken(request.fcmToken());
        } else {
            deviceTokenRepository.save(DeviceToken.create(userId, request.deviceId(), request.fcmToken()));
        }
    }

    public void deleteToken(String deviceId) {
        Long userId = getCurrentUserId();
        DeviceToken token = deviceTokenRepository.findByDeviceIdAndUserId(deviceId, userId)
                .orElseThrow(() -> new CustomException(CustomErrorCode.DEVICE_TOKEN_NOT_FOUND));
        deviceTokenRepository.delete(token);
    }

    private Long getCurrentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}

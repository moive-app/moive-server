package com.moive.MoiveBE.domain.notification.dto;

public record RegisterDeviceTokenRequest(String fcmToken, String deviceId) {}

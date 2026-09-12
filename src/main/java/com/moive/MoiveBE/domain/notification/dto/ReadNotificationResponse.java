package com.moive.MoiveBE.domain.notification.dto;

public record ReadNotificationResponse(
        Long notificationId,
        boolean isRead,
        boolean hasUnreadRemaining
) {}

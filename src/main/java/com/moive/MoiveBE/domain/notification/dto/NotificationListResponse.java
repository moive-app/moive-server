package com.moive.MoiveBE.domain.notification.dto;

import java.time.LocalDateTime;
import java.util.List;

public record NotificationListResponse(
        List<NotificationDto> notifications,
        boolean hasNext,
        Long nextCursor
) {
    public record NotificationDto(
            Long notificationId,
            String type,
            String title,
            String content,
            Long meetingId,
            boolean isRead,
            LocalDateTime createdAt
    ) {}
}

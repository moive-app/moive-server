package com.moive.MoiveBE.domain.notification.service;

import com.moive.MoiveBE.domain.notification.dto.*;
import com.moive.MoiveBE.domain.notification.entity.DeviceToken;
import com.moive.MoiveBE.domain.notification.entity.Notification;
import com.moive.MoiveBE.domain.notification.entity.NotificationType;
import com.moive.MoiveBE.domain.notification.repository.DeviceTokenRepository;
import com.moive.MoiveBE.domain.notification.repository.NotificationRepository;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final FcmService fcmService;

    private static final int RETENTION_DAYS = 30;

    public NotificationListResponse getNotifications(Long cursor, int size) {
        Long userId = getCurrentUserId();
        LocalDateTime from = LocalDateTime.now().minusDays(RETENTION_DAYS);

        List<Notification> results = notificationRepository.findByUserIdWithCursor(userId, from, cursor, size + 1);

        boolean hasNext = results.size() > size;
        List<Notification> notifications = hasNext ? results.subList(0, size) : results;

        Long nextCursor = hasNext ? notifications.get(notifications.size() - 1).getId() : null;

        List<NotificationListResponse.NotificationDto> dtos = notifications.stream()
                .map(n -> new NotificationListResponse.NotificationDto(
                        n.getId(),
                        n.getType().name(),
                        n.getTitle(),
                        n.getContent(),
                        n.getMeetingId(),
                        n.isRead(),
                        n.getCreatedAt()
                ))
                .toList();

        return new NotificationListResponse(dtos, hasNext, nextCursor);
    }

    @Transactional
    public ReadNotificationResponse readNotification(Long notificationId) {
        Long userId = getCurrentUserId();

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new CustomException(CustomErrorCode.NOTIFICATION_NOT_FOUND));

        if (!notification.getUserId().equals(userId)) {
            throw new CustomException(CustomErrorCode.NOTIFICATION_FORBIDDEN);
        }

        notification.markAsRead();

        LocalDateTime from = LocalDateTime.now().minusDays(RETENTION_DAYS);
        boolean hasUnread = notificationRepository.existsByUserIdAndIsReadFalseAndCreatedAtAfter(userId, from);

        return new ReadNotificationResponse(notificationId, true, hasUnread);
    }

    public UnreadStatusResponse getUnreadStatus() {
        Long userId = getCurrentUserId();
        LocalDateTime from = LocalDateTime.now().minusDays(RETENTION_DAYS);
        boolean hasUnread = notificationRepository.existsByUserIdAndIsReadFalseAndCreatedAtAfter(userId, from);
        return new UnreadStatusResponse(hasUnread);
    }

    @Transactional
    public void autoReadByType(Long userId, Long meetingId, NotificationType type) {
        notificationRepository.findByUserIdAndMeetingIdAndTypeAndIsReadFalse(userId, meetingId, type)
                .forEach(Notification::markAsRead);
    }

    @Transactional
    public void sendNotification(Long userId, Long meetingId, NotificationType type, String content) {
        String title = type.getDefaultTitle();

        Notification notification = Notification.create(userId, meetingId, type, title, content);
        notificationRepository.save(notification);

        List<String> tokens = deviceTokenRepository.findAllByUserId(userId).stream()
                .map(DeviceToken::getFcmToken)
                .toList();

        if (!tokens.isEmpty()) {
            fcmService.sendToTokens(tokens, title, content);
        }
    }

    private Long getCurrentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}

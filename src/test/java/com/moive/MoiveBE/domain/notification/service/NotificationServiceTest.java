package com.moive.MoiveBE.domain.notification.service;

import com.moive.MoiveBE.domain.notification.dto.NotificationListResponse;
import com.moive.MoiveBE.domain.notification.dto.ReadNotificationResponse;
import com.moive.MoiveBE.domain.notification.dto.UnreadStatusResponse;
import com.moive.MoiveBE.domain.notification.entity.DeviceToken;
import com.moive.MoiveBE.domain.notification.entity.Notification;
import com.moive.MoiveBE.domain.notification.entity.NotificationType;
import com.moive.MoiveBE.domain.notification.repository.DeviceTokenRepository;
import com.moive.MoiveBE.domain.notification.repository.NotificationRepository;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private DeviceTokenRepository deviceTokenRepository;
    @Mock private FcmService fcmService;

    private NotificationService notificationService;

    private static final Long USER_ID = 1L;
    private static final Long NOTIFICATION_ID = 10L;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, deviceTokenRepository, fcmService);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER_ID, null, List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 알림_목록을_정상_조회한다() {
        Notification n = mockNotification(NOTIFICATION_ID, USER_ID, NotificationType.COND_INPUT, 10L, false);
        when(notificationRepository.findByUserIdWithCursor(eq(USER_ID), any(), eq(null), eq(21))).thenReturn(List.of(n));

        NotificationListResponse response = notificationService.getNotifications(null, 20);

        assertThat(response.notifications()).hasSize(1);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.notifications().get(0).type()).isEqualTo("COND_INPUT");
    }

    @Test
    void hasNext가_true이면_nextCursor가_반환된다() {
        List<Notification> results = List.of(
                mockNotification(1L, USER_ID, NotificationType.COND_INPUT, 10L, false),
                mockNotification(2L, USER_ID, NotificationType.PLACE_VOTE, 10L, false)
        );
        when(notificationRepository.findByUserIdWithCursor(eq(USER_ID), any(), eq(null), eq(2))).thenReturn(results);

        NotificationListResponse response = notificationService.getNotifications(null, 1);

        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isEqualTo(1L);
        assertThat(response.notifications()).hasSize(1);
    }

    @Test
    void 알림_읽음_처리를_정상_수행한다() {
        Notification n = mockNotification(NOTIFICATION_ID, USER_ID, NotificationType.PLACE_RECOMMEND, null, false);
        when(notificationRepository.findById(NOTIFICATION_ID)).thenReturn(Optional.of(n));
        when(notificationRepository.existsByUserIdAndIsReadFalseAndCreatedAtAfter(eq(USER_ID), any())).thenReturn(false);

        ReadNotificationResponse response = notificationService.readNotification(NOTIFICATION_ID);

        verify(n).markAsRead();
        assertThat(response.notificationId()).isEqualTo(NOTIFICATION_ID);
        assertThat(response.isRead()).isTrue();
        assertThat(response.hasUnreadRemaining()).isFalse();
    }

    @Test
    void 존재하지_않는_알림이면_NOTIFICATION_NOT_FOUND_예외가_발생한다() {
        when(notificationRepository.findById(NOTIFICATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.readNotification(NOTIFICATION_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getCustomErrorCode())
                        .isEqualTo(CustomErrorCode.NOTIFICATION_NOT_FOUND));
    }

    @Test
    void 본인_알림이_아니면_NOTIFICATION_FORBIDDEN_예외가_발생한다() {
        Notification n = mockNotification(NOTIFICATION_ID, 999L, NotificationType.COND_INPUT, 10L, false);
        when(notificationRepository.findById(NOTIFICATION_ID)).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> notificationService.readNotification(NOTIFICATION_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getCustomErrorCode())
                        .isEqualTo(CustomErrorCode.NOTIFICATION_FORBIDDEN));
    }

    @Test
    void 안읽은_알림이_있으면_hasUnread가_true이다() {
        when(notificationRepository.existsByUserIdAndIsReadFalseAndCreatedAtAfter(eq(USER_ID), any())).thenReturn(true);

        UnreadStatusResponse response = notificationService.getUnreadStatus();

        assertThat(response.hasUnread()).isTrue();
    }

    @Test
    void 안읽은_알림이_없으면_hasUnread가_false이다() {
        when(notificationRepository.existsByUserIdAndIsReadFalseAndCreatedAtAfter(eq(USER_ID), any())).thenReturn(false);

        UnreadStatusResponse response = notificationService.getUnreadStatus();

        assertThat(response.hasUnread()).isFalse();
    }

    @Test
    void 알림_발송_시_DB저장과_FCM발송이_모두_수행된다() {
        DeviceToken token = mock(DeviceToken.class);
        when(token.getFcmToken()).thenReturn("fcm-token-123");
        when(deviceTokenRepository.findAllByUserId(USER_ID)).thenReturn(List.of(token));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificationService.sendNotification(USER_ID, 10L, NotificationType.COND_INPUT, "조건을 입력해주세요.");

        verify(notificationRepository).save(any(Notification.class));
        verify(fcmService).sendToTokens(List.of("fcm-token-123"), NotificationType.COND_INPUT.getDefaultTitle(), "조건을 입력해주세요.");
    }

    @Test
    void FCM_토큰이_없으면_발송하지_않는다() {
        when(deviceTokenRepository.findAllByUserId(USER_ID)).thenReturn(List.of());
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificationService.sendNotification(USER_ID, 10L, NotificationType.COND_INPUT, "조건을 입력해주세요.");

        verify(fcmService, never()).sendToTokens(any(), any(), any());
    }

    private Notification mockNotification(Long id, Long userId, NotificationType type, Long meetingId, boolean isRead) {
        Notification n = mock(Notification.class);
        lenient().when(n.getId()).thenReturn(id);
        lenient().when(n.getUserId()).thenReturn(userId);
        lenient().when(n.getType()).thenReturn(type);
        lenient().when(n.getTitle()).thenReturn(type.getDefaultTitle());
        lenient().when(n.getContent()).thenReturn("테스트 내용");
        lenient().when(n.getMeetingId()).thenReturn(meetingId);
        lenient().when(n.isRead()).thenReturn(isRead);
        lenient().when(n.getCreatedAt()).thenReturn(LocalDateTime.now());
        return n;
    }
}

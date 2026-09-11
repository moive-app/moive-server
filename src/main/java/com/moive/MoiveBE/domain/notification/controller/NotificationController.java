package com.moive.MoiveBE.domain.notification.controller;

import com.moive.MoiveBE.domain.notification.dto.NotificationListResponse;
import com.moive.MoiveBE.domain.notification.dto.ReadNotificationResponse;
import com.moive.MoiveBE.domain.notification.dto.UnreadStatusResponse;
import com.moive.MoiveBE.domain.notification.service.NotificationService;
import com.moive.MoiveBE.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Notification", description = "알림 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "알림함 목록 조회", description = "최신순, cursor 기반 무한스크롤. 30일 이내 알림만 반환합니다.")
    @GetMapping
    public BaseResponse<NotificationListResponse> getNotifications(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        return BaseResponse.success("알림함 조회에 성공했습니다.", notificationService.getNotifications(cursor, size));
    }

    @Operation(summary = "알림 읽음 처리", description = "알림 클릭 시 호출. 이미 읽은 알림에 재호출해도 정상 처리됩니다.")
    @PatchMapping("/{notificationId}/read")
    public BaseResponse<ReadNotificationResponse> readNotification(
            @PathVariable Long notificationId
    ) {
        return BaseResponse.success("알림을 읽음 처리했습니다.", notificationService.readNotification(notificationId));
    }

    @Operation(summary = "안읽음 뱃지 조회", description = "홈 화면 알림 아이콘 dot 표시용. 안읽은 알림 존재 여부를 반환합니다.")
    @GetMapping("/unread-status")
    public BaseResponse<UnreadStatusResponse> getUnreadStatus() {
        return BaseResponse.success("안읽음 상태 조회에 성공했습니다.", notificationService.getUnreadStatus());
    }
}

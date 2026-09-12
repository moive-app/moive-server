package com.moive.MoiveBE.domain.notification.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {
    COND_INPUT("조건 입력을 완료해주세요."),
    PLACE_RECOMMEND("장소 추천이 완료됐어요."),
    PLACE_VOTE("장소 투표를 완료해주세요."),
    MEETING_CONFIRMED("모임 일정과 장소가 결정됐어요!"),
    APP_UPDATE("업데이트"),
    SERVICE_INCIDENT("장애 및 점검");

    private final String defaultTitle;
}

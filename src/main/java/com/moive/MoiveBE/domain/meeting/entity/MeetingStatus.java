package com.moive.MoiveBE.domain.meeting.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MeetingStatus {
    CONDITION_INPUT("조건 입력중"),
    VOTING("투표 진행중"),
    CONFIRMED("확정"),
    COMPLETED("완료");

    private final String label;
}
package com.moive.MoiveBE.domain.meeting.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ParticipantState {
    COND_PENDING("조건 입력 전"),
    COND_DONE("조건 입력 완료"),
    VOTE_PENDING("투표 전"),
    VOTE_DONE("투표 완료"),
    NEW_RESTRICTED("신규 참여");

    private final String label;
}

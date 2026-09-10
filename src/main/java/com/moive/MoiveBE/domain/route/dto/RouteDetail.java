package com.moive.MoiveBE.domain.route.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record RouteDetail(
        // 이동 경로상의 좌표 정보
        List<Location> pathPoints,

        // 이동 시간 정보
        int totalTime,
        int walkTime,
        int busTime,
        int subwayTime,
        int transferCnt,
        int fare,
        String landingUrl
) {
}

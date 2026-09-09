package com.moive.MoiveBE.domain.route.dto;

import com.moive.MoiveBE.domain.route.type.TransitType;
import lombok.Builder;

import java.util.List;

@Builder
public record RouteDetailResponse(
        // 유저 정보
        String nickname,

        // 지도에 표시하는 정보
        Location userLocation,
        Location placeLocation,
        List<RouteStep> routeSteps,

        // 이동 시간 정보
        int totalTime,
        int walkTime,
        int busTime,
        int subwayTime,
        Integer fare,
        String landingUrl
) {

    public record RouteStep(
            TransitType type,
            List<Location> path
    ) {
    }
}

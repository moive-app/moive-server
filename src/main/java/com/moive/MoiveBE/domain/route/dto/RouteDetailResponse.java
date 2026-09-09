package com.moive.MoiveBE.domain.route.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record RouteDetailResponse(
        // 유저 정보
        String nickname,

        // 지도에 표시하는 정보
        Location userLocation,
        Location placeLocation,
        List<Location> pathPoints,

        // 이동 시간 정보
        int totalTime,
        int walkTime,
        int busTime,
        int subwayTime,
        Integer fare,
        String landingUrl
) {

    public static RouteDetailResponse of(
            String nickname,
            Location userLocation,
            Location placeLocation,
            RouteDetail routeDetail
    ) {
        return RouteDetailResponse.builder()
                .nickname(nickname)
                .userLocation(userLocation)
                .placeLocation(placeLocation)
                .pathPoints(routeDetail.pathPoints())
                .totalTime(routeDetail.totalTime())
                .walkTime(routeDetail.walkTime())
                .busTime(routeDetail.busTime())
                .subwayTime(routeDetail.subwayTime())
                .fare(routeDetail.fare())
                .landingUrl(routeDetail.landingUrl())
                .build();
    }
}

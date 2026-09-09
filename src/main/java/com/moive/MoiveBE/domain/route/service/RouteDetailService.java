package com.moive.MoiveBE.domain.route.service;

import com.moive.MoiveBE.domain.route.client.KakaoTransitClient;
import com.moive.MoiveBE.domain.route.dto.KakaoTransitRouteResponse;
import com.moive.MoiveBE.domain.route.dto.Location;
import com.moive.MoiveBE.domain.route.dto.RouteDetailResponse;
import com.moive.MoiveBE.domain.route.type.TransitType;
import com.moive.MoiveBE.domain.route.type.KakaoTransitStatusType;
import com.moive.MoiveBE.domain.route.util.PathPointsSimplifier;
import com.moive.MoiveBE.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.moive.MoiveBE.global.exception.CustomErrorCode.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class RouteDetailService {

    private final int FIRST_ROUTE_IDX = 0;

    private final KakaoTransitClient kakaoTransitClient;

    public RouteDetailResponse getMyRouteDetail(
            Location userLocation,
            Location placeLocation
    ) {
        // 카카오맵 대중교통 경로 조회 API 호출
        KakaoTransitRouteResponse kakaoResponse = kakaoTransitClient.getTransitRoute(userLocation, placeLocation);
        if (kakaoResponse == null || kakaoResponse.status() == null) {
            log.error("[카카오맵 대중교통 경로 조회 API] Response Body가 비어있습니다.");
            throw new CustomException(KAKAO_MAP_API_INVALID_RESPONSE);
        }

        KakaoTransitStatusType status = KakaoTransitStatusType.valueOf(kakaoResponse.status());
        return switch (status) {
            // 대중교통 경로가 존재하는 경우 => 첫번째 경로 활용
            case OK -> {
                KakaoTransitRouteResponse.Route bestRoute = kakaoResponse.routes().get(FIRST_ROUTE_IDX);
                KakaoTransitRouteResponse.RouteProperties totalSummary = bestRoute.properties();

                TransitTimeSummary timeSummary = calculateTransitTimeByType(bestRoute);
                int totalSeconds = totalSummary.totalTime();
                int busSeconds = timeSummary.busSeconds();
                int subwaySeconds = timeSummary.subwaySeconds();
                int walkSeconds = Math.max(0, totalSeconds - (busSeconds + subwaySeconds));

                List<RouteDetailResponse.RouteStep> routeSteps =
                        addBoundaryWalkingSteps(getRouteSteps(bestRoute), userLocation, placeLocation);

                Integer fare = totalSummary.fare() != null ? totalSummary.fare().value() : null;

                yield RouteDetailResponse.builder()
                        .userLocation(userLocation)
                        .placeLocation(placeLocation)
                        .routeSteps(routeSteps)
                        .totalTime(toMinutes(totalSeconds))
                        .subwayTime(toMinutes(subwaySeconds))
                        .busTime(toMinutes(busSeconds))
                        .walkTime(toMinutes(walkSeconds))
                        .fare(fare)
                        .landingUrl(kakaoResponse.properties().landingURL())
                        .build();
            }

            // 대중교통 경로 조회가 불가능한 경우
            case STARTNODES_NULL, ENDNODES_NULL, INVALID_REQUEST -> {
                log.error("[카카오맵 대중교통 경로 조회 API] 요청 좌표로 경로 탐색이 불가능합니다. (status={})", status);
                throw new CustomException(KAKAO_MAP_API_INVALID_REQUEST);
            }

            // 대중교통 경로가 존재하지 않는 경우
            case EQUAL_POINTS, NO_RESULTS -> {
                // TODO: NO_RESULTS인 경우 도보 API 연동 추가 예정
                log.warn("[카카오맵 대중교통 경로 조회 API] 경로가 존재하지 않습니다. (status={})", status);
                throw new CustomException(TRANSIT_ROUTE_NOT_FOUND);
            }
        };
    }

    // 이동 수단(WALKING/BUS/SUBWAY)별 이동 시간 누적합(초 단위) 계산
    TransitTimeSummary calculateTransitTimeByType(KakaoTransitRouteResponse.Route bestRoute) {
        List<KakaoTransitRouteResponse.Step> steps = bestRoute.steps();

        int busSeconds = 0;
        int subwaySeconds = 0;

        for (KakaoTransitRouteResponse.Step step : steps) {
            KakaoTransitRouteResponse.StepProperties stepProperties = step.properties();
            TransitType type = TransitType.fromKakaoType(stepProperties.type());
            int stepSeconds = stepProperties.time() != null ? stepProperties.time() : 0;

            switch (type) {
                case BUS -> busSeconds += stepSeconds;
                case SUBWAY -> subwaySeconds += stepSeconds;
            }
        }

        return new TransitTimeSummary(subwaySeconds, busSeconds);
    }

    record TransitTimeSummary(int subwaySeconds, int busSeconds) {
    }

    // 각 step의 이동 수단과 경로 좌표(path)로 RouteStep 목록 구성
    List<RouteDetailResponse.RouteStep> getRouteSteps(KakaoTransitRouteResponse.Route bestRoute) {
        List<KakaoTransitRouteResponse.Step> steps = bestRoute.steps();
        List<RouteDetailResponse.RouteStep> routeSteps = new ArrayList<>();

        for (KakaoTransitRouteResponse.Step step : steps) {
            TransitType type = TransitType.fromKakaoType(step.properties().type());
            List<Location> simplifiedPath = PathPointsSimplifier.simplify(getPathPoints(step.path()));
            routeSteps.add(new RouteDetailResponse.RouteStep(type, simplifiedPath));
        }

        return routeSteps;
    }

    // userLocation -> 첫 탑승 지점, 마지막 하차 지점 -> placeLocation 구간 도보 직선 경로로 추가
    List<RouteDetailResponse.RouteStep> addBoundaryWalkingSteps(
            List<RouteDetailResponse.RouteStep> routeSteps,
            Location userLocation,
            Location placeLocation
    ) {
        Location firstTransitPoint = routeSteps.get(0).path().get(0);
        RouteDetailResponse.RouteStep lastStep = routeSteps.get(routeSteps.size() - 1);
        Location lastTransitPoint = lastStep.path().get(lastStep.path().size() - 1);

        List<RouteDetailResponse.RouteStep> result = new ArrayList<>();
        result.add(new RouteDetailResponse.RouteStep(TransitType.WALKING, List.of(userLocation, firstTransitPoint)));
        result.addAll(routeSteps);
        result.add(new RouteDetailResponse.RouteStep(TransitType.WALKING, List.of(lastTransitPoint, placeLocation)));

        return result;
    }

    // 카카오 원본 좌표 전부 반환
    private List<Location> getPathPoints(KakaoTransitRouteResponse.Path path) {
        return Arrays.stream(path.points())
                .map(point -> new Location(point[1], point[0]))
                .toList();
    }

    // 초 -> 분 변환 (반올림)
    private int toMinutes(int seconds) {
        return Math.round(seconds / 60.0f);
    }

}
